package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.TouristRegistrationForm;
import uy.edu.um.luminalabs.entities.Tourist;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.TouristRegisteredEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// CU-12: registrarse como turista
@Service
@RequiredArgsConstructor
public class TouristRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Tourist register(TouristRegistrationForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        accountValidator.validateNewAccount(form, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Tourist tourist = userRepository.save(Tourist.builder()
                .firstName(form.getFirstName().trim())
                .lastName(form.getLastName().trim())
                .birthDate(form.getBirthDate())
                .username(form.getUsername().trim())
                .email(AccountValidator.normalizeEmail(form.getEmail()))
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .build());

        eventPublisher.publishEvent(new TouristRegisteredEvent(MailRecipient.from(tourist), tourist.getUsername()));
        return tourist;
    }
}
