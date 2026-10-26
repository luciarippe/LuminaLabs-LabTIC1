package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.InvitationAcceptForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.ProviderInvitation;
import uy.edu.um.luminalabs.entities.Role;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.ProviderTeamEvent;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.ProviderInvitationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// CU-15: los prestadores de un emprendimiento aprobado suman y quitan a otros prestadores. El emprendimiento
// conserva siempre al menos uno (Business.removeProvider).
// Si el correo no tiene cuenta (4a) no se crea una cuenta a medias: se manda una invitacion con un enlace, y la
// cuenta de prestador se crea, ya asociada, cuando la persona completa sus datos y elige su contrasena.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderTeamService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ProviderInvitationRepository invitationRepository;
    private final AccountValidator accountValidator;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Que paso al agregar: se asocio una cuenta existente (paso 5) o se mando una invitacion (4a)
    public enum Outcome { ADDED, INVITED, INVITATION_RESENT }

    public record AddResult(Outcome outcome, String email, String name) {
    }

    // Paso 2. Un emprendimiento de otro prestador se trata como inexistente (404).
    public Business findManagedBusiness(Long providerId, Long businessId) {
        return businessRepository.findWithDetailsById(businessId)
                .filter(business -> business.hasProvider(providerId))
                .orElseThrow(() -> new BusinessNotFoundException(businessId));
    }

    // Los prestadores del emprendimiento, ordenados por nombre
    public List<Provider> providersOf(Business business) {
        return business.getProviders().stream()
                .sorted(Comparator.comparing(Provider::getFirstName).thenComparing(Provider::getLastName))
                .toList();
    }

    public List<ProviderInvitation> pendingInvitations(Long businessId) {
        return invitationRepository.findByBusiness_IdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                businessId, LocalDateTime.now(clock));
    }

    // Pasos 3 a 5 y 4a a 4c
    @Transactional
    public AddResult add(Long providerId, Long businessId, String rawEmail) {
        Business business = findManagedBusiness(providerId, businessId);
        requireApproved(business);
        Provider by = findProvider(providerId);
        String email = AccountValidator.normalizeEmail(rawEmail);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return invite(business, email, by);
        }
        // 4b: cada usuario tiene un unico rol
        if (!(user instanceof Provider provider)) {
            throw new FieldValidationException(Map.of("email", "Ese correo es de una cuenta de "
                    + (user.getRole() == Role.TOURIST ? "turista" : "administrador")
                    + ". Cada cuenta tiene un único rol, así que no se puede sumar como prestador."));
        }
        // 4c
        if (business.hasProvider(provider.getId())) {
            throw new FieldValidationException(Map.of("email",
                    provider.getFullName() + " ya es prestador de este emprendimiento."));
        }
        business.addProvider(provider);
        eventPublisher.publishEvent(new ProviderTeamEvent.Added(MailRecipient.from(provider), business.getLegalName(),
                by.getFullName()));
        return new AddResult(Outcome.ADDED, email, provider.getFullName());
    }

    // 4a. Si ya habia una invitacion pendiente a ese correo, se reemplaza por una nueva (el enlace anterior deja
    // de servir) y se vuelve a mandar.
    private AddResult invite(Business business, String email, Provider by) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ProviderInvitation> previous = pendingInvitations(business.getId()).stream()
                .filter(invitation -> invitation.getEmail().equals(email))
                .toList();
        previous.forEach(invitation -> invitation.revoke(now));

        String token = newToken();
        ProviderInvitation invitation = invitationRepository.save(
                new ProviderInvitation(business, email, hash(token), by, now));
        eventPublisher.publishEvent(new ProviderTeamEvent.Invited(email, business.getLegalName(), by.getFullName(),
                token, invitation.getExpiresAt()));
        return new AddResult(previous.isEmpty() ? Outcome.INVITED : Outcome.INVITATION_RESENT, email, null);
    }

    // 3a y 3b. Se quita a otro prestador (para irse uno mismo, que lo quite otro).
    @Transactional
    public Provider remove(Long providerId, Long businessId, Long removedProviderId) {
        Business business = findManagedBusiness(providerId, businessId);
        requireApproved(business);
        if (removedProviderId.equals(providerId)) {
            throw new IllegalStateException("No podés quitarte a vos mismo: pedile a otro prestador del emprendimiento que lo haga.");
        }
        Provider removed = business.getProviders().stream()
                .filter(provider -> provider.getId().equals(removedProviderId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Esa persona no es prestador de este emprendimiento."));
        business.removeProvider(removed);
        eventPublisher.publishEvent(new ProviderTeamEvent.Removed(MailRecipient.from(removed), business.getLegalName(),
                findProvider(providerId).getFullName()));
        return removed;
    }

    @Transactional
    public void cancelInvitation(Long providerId, Long businessId, Long invitationId) {
        findManagedBusiness(providerId, businessId);
        pendingInvitations(businessId).stream()
                .filter(invitation -> invitation.getId().equals(invitationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La invitación ya no está pendiente."))
                .revoke(LocalDateTime.now(clock));
    }

    // ---------- Aceptar la invitacion (4a) ----------

    // El enlace del correo. Si no existe, ya se uso, vencio o se cancelo, no se puede usar.
    public ProviderInvitation findPendingInvitation(String token) {
        ProviderInvitation invitation = invitationRepository.findByTokenHash(hash(token))
                .filter(found -> found.isPending(LocalDateTime.now(clock)))
                .orElseThrow(() -> new IllegalStateException(
                        "Este enlace de invitación ya no es válido: puede que se haya usado, vencido o cancelado."));
        if (!invitation.getBusiness().isApproved()) {
            throw new IllegalStateException("El emprendimiento " + invitation.getBusiness().getLegalName()
                    + " no está activo en este momento, así que no se puede aceptar la invitación.");
        }
        return invitation;
    }

    // Se crea la cuenta de prestador con los datos del formulario y el correo de la invitacion, ya asociada
    @Transactional
    public Provider accept(String token, InvitationAcceptForm form) {
        ProviderInvitation invitation = findPendingInvitation(token);
        form.setEmail(invitation.getEmail());
        Map<String, String> errors = new LinkedHashMap<>();
        accountValidator.validateNewAccount(form, errors);
        if (errors.containsKey("email")) {
            errors.put("email", "Ya existe una cuenta con este correo. Pedile a quien te invitó que te agregue de nuevo.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Provider provider = userRepository.save(Provider.builder()
                .firstName(form.getFirstName().trim())
                .lastName(form.getLastName().trim())
                .birthDate(form.getBirthDate())
                .username(form.getUsername().trim())
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .build());
        invitation.getBusiness().addProvider(provider);
        invitation.accept(LocalDateTime.now(clock));
        return provider;
    }

    // ---------- Auxiliares ----------

    private static void requireApproved(Business business) {
        if (!business.isApproved()) {
            throw new IllegalStateException("Solo se pueden cambiar los prestadores de un emprendimiento aprobado.");
        }
    }

    private Provider findProvider(Long providerId) {
        return (Provider) userRepository.findById(providerId).orElseThrow();
    }

    // 32 bytes al azar, en un formato que se puede poner en un enlace
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
