package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.ProviderRegistrationForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// CU-02: registrar un emprendimiento junto con la cuenta de su prestador
@Service
@RequiredArgsConstructor
public class ProviderRegistrationService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Business register(ProviderRegistrationForm form) {
        String identifier = normalizeIdentifier(form.getIdentifier());
        String email = AccountValidator.normalizeEmail(form.getEmail());
        String username = form.getUsername().trim();
        List<Long> locationIds = form.getLocationIds().stream().distinct().toList();
        List<Location> locations = locationRepository.findAllById(locationIds);

        Map<String, String> errors = new LinkedHashMap<>();
        if (!isValidIdentifier(form.getBusinessType(), identifier)) {
            errors.put("identifier", form.getBusinessType() == BusinessType.COMPANY
                    ? "El RUT debe tener 12 dígitos."
                    : "La cédula debe tener 7 u 8 dígitos.");
        } else if (businessRepository.existsByIdentifier(identifier)) {
            errors.put("identifier", "Ya existe un emprendimiento registrado con este identificador.");
        }
        if (locations.isEmpty() || locations.size() != locationIds.size()) {
            errors.put("locationIds", "Elegí al menos una localidad válida.");
        }
        accountValidator.validateNewAccount(form, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Provider provider = Provider.builder()
                .firstName(form.getFirstName().trim())
                .lastName(form.getLastName().trim())
                .birthDate(form.getBirthDate())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .build();
        userRepository.save(provider);

        Business business = new Business();
        business.setType(form.getBusinessType());
        business.setLegalName(form.getLegalName().trim());
        business.setIdentifier(identifier);
        business.setDescription(form.getDescription().trim());
        business.setContactEmail(form.getContactEmail().trim().toLowerCase(Locale.ROOT));
        business.setContactPhone(form.getContactPhone().trim());
        business.setBankName(form.getBankName().trim());
        business.setBankAccountNumber(form.getBankAccountNumber().trim());
        business.getLocations().addAll(locations);
        business.addProvider(provider);
        Business saved = businessRepository.save(business);

        eventPublisher.publishEvent(new BusinessRegisteredEvent(MailRecipient.from(provider), saved.getLegalName()));
        return saved;
    }

    // Acepta "1.234.567-8" o "21 1234560019" y guarda solo los digitos
    static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.replaceAll("\\D", "");
    }

    static boolean isValidIdentifier(BusinessType type, String digits) {
        return switch (type) {
            case COMPANY -> digits.matches("\\d{12}");
            case INDIVIDUAL -> digits.matches("\\d{7,8}");
        };
    }
}
