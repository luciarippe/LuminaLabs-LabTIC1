package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.BusinessForm;
import uy.edu.um.luminalabs.dto.ProviderRegistrationForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// CU-02: registrar un emprendimiento. Tres casos:
//  - registro publico: se crea el emprendimiento junto con la cuenta de su prestador;
//  - 1a: un prestador ya logueado agrega otro emprendimiento a su cuenta;
//  - 5c: si el identificador es de un emprendimiento denegado, se actualizan sus datos y vuelve a "pendiente".
@Service
@RequiredArgsConstructor
public class ProviderRegistrationService {

    // Prefijo de los campos del emprendimiento en los formularios (ver BusinessForm)
    private static final String BUSINESS = "business.";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SocialLinksService socialLinksService;

    @Transactional
    public Business register(ProviderRegistrationForm form) {
        BusinessForm businessForm = form.getBusiness();
        Map<String, String> errors = new LinkedHashMap<>();
        List<Location> locations = validateBusiness(businessForm, errors);
        Optional<Business> existing = findExisting(businessForm, errors);

        Provider provider;
        if (existing.isPresent() && existing.get().getStatus() == BusinessStatus.DENIED) {
            // 5c sin sesion: solo puede volver a postularlo un prestador que ya pertenece al emprendimiento,
            // identificandose con los datos de su cuenta. Si no, cualquiera que conozca el RUT podria tomarlo.
            provider = findOwnAccount(existing.get(), form, errors);
        } else {
            accountValidator.validateNewAccount(form, errors);
            provider = null;
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        if (provider == null) {
            provider = userRepository.save(Provider.builder()
                    .firstName(form.getFirstName().trim())
                    .lastName(form.getLastName().trim())
                    .birthDate(form.getBirthDate())
                    .username(form.getUsername().trim())
                    .email(AccountValidator.normalizeEmail(form.getEmail()))
                    .passwordHash(passwordEncoder.encode(form.getPassword()))
                    .build());
        }
        return save(existing, businessForm, locations, provider);
    }

    // CU-02 1a: el prestador ya inicio sesion, no ingresa datos de cuenta
    @Transactional
    public Business registerForProvider(Long providerId, BusinessForm businessForm) {
        Provider provider = (Provider) userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalStateException("Provider not found: " + providerId));
        Map<String, String> errors = new LinkedHashMap<>();
        List<Location> locations = validateBusiness(businessForm, errors);
        Optional<Business> existing = findExisting(businessForm, errors);
        if (existing.isPresent() && existing.get().getStatus() == BusinessStatus.DENIED
                && !existing.get().hasProvider(providerId)) {
            errors.put(BUSINESS + "identifier", "Ya existe un emprendimiento registrado con este identificador.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return save(existing, businessForm, locations, provider);
    }

    // Formato del identificador y localidades. Devuelve las localidades elegidas.
    private List<Location> validateBusiness(BusinessForm form, Map<String, String> errors) {
        socialLinksService.validate(form.getSocial(), BUSINESS + "social.", errors);
        String identifier = normalizeIdentifier(form.getIdentifier());
        if (!isValidIdentifier(form.getBusinessType(), identifier)) {
            errors.put(BUSINESS + "identifier", form.getBusinessType() == BusinessType.COMPANY
                    ? "El RUT debe tener 12 dígitos."
                    : "La cédula debe tener 7 u 8 dígitos.");
        }
        String accountError = form.getBank().accountNumberError(form.getBankAccountNumber());
        if (accountError != null) {
            errors.put(BUSINESS + "bankAccountNumber", accountError);
        }
        List<Long> locationIds = form.getLocationIds().stream().distinct().toList();
        List<Location> locations = locationRepository.findAllById(locationIds);
        if (locations.isEmpty() || locations.size() != locationIds.size()) {
            errors.put(BUSINESS + "locationIds", "Elegí al menos una localidad válida.");
        }
        return locations;
    }

    // 5b: un identificador pendiente, aprobado o suspendido no se puede volver a registrar.
    // Un emprendimiento denegado se devuelve para que el que llama decida si se puede volver a postular.
    private Optional<Business> findExisting(BusinessForm form, Map<String, String> errors) {
        Optional<Business> existing = businessRepository.findByIdentifier(normalizeIdentifier(form.getIdentifier()));
        if (existing.isPresent() && existing.get().getStatus() != BusinessStatus.DENIED) {
            errors.put(BUSINESS + "identifier", "Ya existe un emprendimiento registrado con este identificador.");
        }
        return existing;
    }

    // La cuenta del formulario tiene que ser la de un prestador del emprendimiento denegado (correo,
    // usuario y contrasena correctos). Los mensajes no dicen cual dato fallo, igual que en el login.
    private Provider findOwnAccount(Business denied, ProviderRegistrationForm form, Map<String, String> errors) {
        Optional<Provider> provider = userRepository.findByEmailIgnoreCase(AccountValidator.normalizeEmail(form.getEmail()))
                .filter(user -> user instanceof Provider)
                .map(user -> (Provider) user)
                .filter(user -> denied.hasProvider(user.getId()))
                .filter(user -> user.getUsername().equalsIgnoreCase(form.getUsername().trim()))
                .filter(user -> passwordEncoder.matches(form.getPassword(), user.getPasswordHash()));
        if (provider.isEmpty()) {
            errors.put(BUSINESS + "identifier", "Este emprendimiento tiene una solicitud denegada. Para volver a "
                    + "postularlo, ingresá abajo el correo, el usuario y la contraseña de la cuenta con la que lo registraste.");
        }
        return provider.orElse(null);
    }

    private Business save(Optional<Business> existing, BusinessForm form, List<Location> locations, Provider provider) {
        Business business = existing.orElseGet(() -> {
            Business created = new Business();
            created.setCreatedAt(LocalDateTime.now(clock));
            return created;
        });
        business.setType(form.getBusinessType());
        business.setLegalName(form.getLegalName().trim());
        business.setIdentifier(normalizeIdentifier(form.getIdentifier()));
        business.setDescription(form.getDescription().trim());
        business.setContactEmail(form.getContactEmail().trim().toLowerCase(Locale.ROOT));
        business.setContactPhone(form.getContactPhone().trim());
        business.setBank(form.getBank());
        business.setBankAccountNumber(form.getBankAccountNumber().trim());
        socialLinksService.apply(business, form.getSocial());
        business.getLocations().clear();
        business.getLocations().addAll(locations);
        if (!business.hasProvider(provider.getId())) {
            business.addProvider(provider);
        }
        if (existing.isPresent()) {
            business.resubmit();
        }
        Business saved = businessRepository.save(business);

        eventPublisher.publishEvent(new BusinessRegisteredEvent(MailRecipient.from(provider), saved.getLegalName()));
        return saved;
    }

    // BusinessForm ya exige solo numeros; esto ademas descarta cualquier otro caracter por las dudas
    static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.replaceAll("\\D", "");
    }

    static boolean isValidIdentifier(BusinessType type, String digits) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case COMPANY -> digits.matches("\\d{12}");
            case INDIVIDUAL -> digits.matches("\\d{7,8}");
        };
    }
}
