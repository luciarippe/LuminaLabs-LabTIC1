package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.InvitationAcceptForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.ProviderTeamEvent;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.ProviderInvitationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-15: gestionar los prestadores de un emprendimiento
@ExtendWith(MockitoExtension.class)
class ProviderTeamServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long BUSINESS_ID = 4L;

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProviderInvitationRepository invitationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProviderTeamService service;
    private Business business;
    private Provider ana;
    private Provider bruno;

    @BeforeEach
    void setUp() {
        service = new ProviderTeamService(businessRepository, userRepository, invitationRepository,
                new AccountValidator(userRepository), passwordEncoder, eventPublisher,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        ana = provider(1L, "Ana", "ana@correo.uy");
        bruno = provider(2L, "Bruno", "bruno@correo.uy");
        business = new Business();
        business.setId(BUSINESS_ID);
        business.setLegalName("Costa Este");
        business.addProvider(ana);
        business.approve(NOW);
    }

    private static Provider provider(Long id, String name, String email) {
        Provider provider = Provider.builder().firstName(name).lastName("Pérez").email(email).username(name.toLowerCase())
                .birthDate(LocalDate.of(1990, 1, 1)).build();
        provider.setId(id);
        return provider;
    }

    private void anaManagesTheBusiness() {
        when(businessRepository.findWithDetailsById(BUSINESS_ID)).thenReturn(Optional.of(business));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(ana));
    }

    // Paso 5: una cuenta de prestador que ya existe se asocia y se le avisa
    @Test
    void addsAnExistingProvider() {
        anaManagesTheBusiness();
        when(userRepository.findByEmailIgnoreCase("bruno@correo.uy")).thenReturn(Optional.of(bruno));

        ProviderTeamService.AddResult result = service.add(1L, BUSINESS_ID, " Bruno@Correo.uy ");

        assertEquals(ProviderTeamService.Outcome.ADDED, result.outcome());
        assertTrue(business.hasProvider(2L));
        assertTrue(bruno.getBusinesses().contains(business));
        verify(eventPublisher).publishEvent(any(ProviderTeamEvent.Added.class));
    }

    // 4b: un turista (o administrador) no puede ser prestador
    @Test
    void touristCannotBeAdded() {
        anaManagesTheBusiness();
        Tourist tourist = Tourist.builder().firstName("Tea").lastName("Gil").email("tea@correo.uy").build();
        when(userRepository.findByEmailIgnoreCase("tea@correo.uy")).thenReturn(Optional.of(tourist));

        FieldValidationException e = assertThrows(FieldValidationException.class,
                () -> service.add(1L, BUSINESS_ID, "tea@correo.uy"));
        assertTrue(e.getFieldErrors().get("email").contains("turista"));
        assertEquals(1, business.getProviders().size());
    }

    // 4c
    @Test
    void alreadyAssociatedProviderIsReported() {
        anaManagesTheBusiness();
        when(userRepository.findByEmailIgnoreCase("ana@correo.uy")).thenReturn(Optional.of(ana));

        assertThrows(FieldValidationException.class, () -> service.add(1L, BUSINESS_ID, "ana@correo.uy"));
    }

    // 4a: sin cuenta se manda una invitacion; en la base queda solo el hash del enlace
    @Test
    void unknownEmailGetsAnInvitation() {
        anaManagesTheBusiness();
        when(userRepository.findByEmailIgnoreCase("nueva@correo.uy")).thenReturn(Optional.empty());
        when(invitationRepository.save(any(ProviderInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderTeamService.AddResult result = service.add(1L, BUSINESS_ID, "nueva@correo.uy");

        assertEquals(ProviderTeamService.Outcome.INVITED, result.outcome());
        ArgumentCaptor<ProviderInvitation> saved = ArgumentCaptor.forClass(ProviderInvitation.class);
        verify(invitationRepository).save(saved.capture());
        ArgumentCaptor<ProviderTeamEvent.Invited> event = ArgumentCaptor.forClass(ProviderTeamEvent.Invited.class);
        verify(eventPublisher).publishEvent(event.capture());
        String token = event.getValue().token();
        assertNotEquals(token, saved.getValue().getTokenHash());
        assertEquals(ProviderTeamService.hash(token), saved.getValue().getTokenHash());
        assertEquals(NOW.plusDays(7), saved.getValue().getExpiresAt());
        assertEquals(1, business.getProviders().size(), "la cuenta se crea recien al aceptar");
    }

    // 3a: se quita a otro prestador y se le avisa
    @Test
    void removesAnotherProvider() {
        business.addProvider(bruno);
        anaManagesTheBusiness();

        service.remove(1L, BUSINESS_ID, 2L);

        assertFalse(business.hasProvider(2L));
        assertFalse(bruno.getBusinesses().contains(business));
        verify(eventPublisher).publishEvent(any(ProviderTeamEvent.Removed.class));
    }

    // 3b: el emprendimiento conserva siempre al menos uno (y nadie se quita a si mismo)
    @Test
    void cannotRemoveYourselfNorTheLastProvider() {
        anaManagesTheBusiness();

        assertThrows(IllegalStateException.class, () -> service.remove(1L, BUSINESS_ID, 1L));
        assertThrows(IllegalStateException.class, () -> business.removeProvider(ana));
        assertTrue(business.hasProvider(1L));
    }

    @Test
    void onlyApprovedBusinessesCanChangeProviders() {
        business.suspend("Motivo", NOW);
        anaManagesTheBusiness();

        assertThrows(IllegalStateException.class, () -> service.add(1L, BUSINESS_ID, "bruno@correo.uy"));
    }

    // Un emprendimiento de otro prestador no existe para el (404)
    @Test
    void otherProvidersBusinessIsNotFound() {
        when(businessRepository.findWithDetailsById(BUSINESS_ID)).thenReturn(Optional.of(business));

        assertThrows(BusinessNotFoundException.class, () -> service.add(99L, BUSINESS_ID, "x@correo.uy"));
    }

    // 4a: al aceptar se crea la cuenta de prestador, con el correo de la invitacion, ya asociada
    @Test
    void acceptingCreatesTheProviderAccount() {
        ProviderInvitation invitation = new ProviderInvitation(business, "nueva@correo.uy",
                ProviderTeamService.hash("token-valido"), ana, NOW.minusDays(1));
        when(invitationRepository.findByTokenHash(ProviderTeamService.hash("token-valido"))).thenReturn(Optional.of(invitation));
        when(passwordEncoder.encode("Secreta123")).thenReturn("hash");
        when(userRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });
        InvitationAcceptForm form = acceptForm();
        form.setEmail("otro@correo.uy");

        Provider created = service.accept("token-valido", form);

        assertEquals("nueva@correo.uy", created.getEmail(), "se usa el correo de la invitacion");
        assertTrue(business.hasProvider(3L));
        assertFalse(invitation.isPending(NOW), "la invitacion sirve una sola vez");
    }

    @Test
    void expiredOrUnknownInvitationCannotBeUsed() {
        ProviderInvitation old = new ProviderInvitation(business, "nueva@correo.uy", ProviderTeamService.hash("viejo"),
                ana, NOW.minusDays(8));
        when(invitationRepository.findByTokenHash(ProviderTeamService.hash("viejo"))).thenReturn(Optional.of(old));
        when(invitationRepository.findByTokenHash(ProviderTeamService.hash("inventado"))).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.findPendingInvitation("viejo"));
        assertThrows(IllegalStateException.class, () -> service.findPendingInvitation("inventado"));
    }

    private static InvitationAcceptForm acceptForm() {
        InvitationAcceptForm form = new InvitationAcceptForm();
        form.setFirstName("Carla");
        form.setLastName("Núñez");
        form.setBirthDate(LocalDate.of(1995, 6, 1));
        form.setUsername("carla.n");
        form.setPassword("Secreta123");
        form.setConfirmPassword("Secreta123");
        return form;
    }
}
