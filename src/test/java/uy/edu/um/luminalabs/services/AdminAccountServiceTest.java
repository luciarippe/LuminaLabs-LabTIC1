package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.AdminForm;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.entities.Role;
import uy.edu.um.luminalabs.events.AdminCreatedEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminAccountService service;
    private AdminForm form;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(userRepository, passwordEncoder, new AccountValidator(userRepository),
                eventPublisher);

        form = new AdminForm();
        form.setFirstName("Ana");
        form.setLastName("Pérez");
        form.setBirthDate(LocalDate.of(1990, 5, 10));
        form.setUsername("anaperez");
        form.setEmail(" Ana@UruguayMnatural.uy ");
        form.setPassword("segura1234");
        form.setConfirmPassword("segura1234");
    }

    @Test
    void createsAdmin() {
        when(passwordEncoder.encode("segura1234")).thenReturn("hashed");
        when(userRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Admin admin = service.create(form);

        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals("ana@uruguaymnatural.uy", admin.getEmail());
        assertEquals("hashed", admin.getPasswordHash());
        verify(eventPublisher).publishEvent(new AdminCreatedEvent(
                new MailRecipient("ana@uruguaymnatural.uy", "Ana"), "anaperez", "segura1234"));
    }

    @Test
    void rejectsExistingUsername() {
        when(userRepository.existsByUsernameIgnoreCase("anaperez")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.create(form));

        assertTrue(exception.getFieldErrors().containsKey("username"));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsPasswordMismatch() {
        form.setConfirmPassword("otra12345");

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.create(form));

        assertTrue(exception.getFieldErrors().containsKey("confirmPassword"));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
