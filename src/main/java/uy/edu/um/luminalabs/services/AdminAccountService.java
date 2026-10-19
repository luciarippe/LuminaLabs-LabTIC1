package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.AdminForm;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.events.AdminCreatedEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// Los administradores no se registran solos: el primero se crea al arrancar (ver DataInitializer)
// y los siguientes los da de alta otro administrador desde el panel. Su propia cuenta la gestionan con AccountService.
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Admin create(AdminForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        accountValidator.validateNewAccount(form, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Admin admin = userRepository.save(Admin.builder()
                .firstName(form.getFirstName().trim())
                .lastName(form.getLastName().trim())
                .birthDate(form.getBirthDate())
                .username(form.getUsername().trim())
                .email(AccountValidator.normalizeEmail(form.getEmail()))
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .build());

        eventPublisher.publishEvent(
                new AdminCreatedEvent(MailRecipient.from(admin), admin.getUsername(), form.getPassword()));
        return admin;
    }
}
