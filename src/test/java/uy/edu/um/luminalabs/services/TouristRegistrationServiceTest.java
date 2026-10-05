package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.TouristRegistrationForm;
import uy.edu.um.luminalabs.entities.Role;
import uy.edu.um.luminalabs.entities.Tourist;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.TouristRegisteredEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TouristRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TouristRegistrationService service;
    private TouristRegistrationForm form;

    @BeforeEach
    void setUp() {
        service = new TouristRegistrationService(userRepository, passwordEncoder,
                new AccountValidator(userRepository), eventPublisher);

        form = new TouristRegistrationForm();
        form.setFirstName("Juan");
        form.setLastName("Gómez");
        form.setBirthDate(LocalDate.of(1995, 3, 20));
        form.setUsername("juangomez");
        form.setEmail(" Juan@Mail.com ");
        form.setPassword("viajero123");
        form.setConfirmPassword("viajero123");
    }

    @Test
    void registersTouristAndSendsWelcomeEmail() {
        when(passwordEncoder.encode("viajero123")).thenReturn("hashed");
        when(userRepository.save(any(Tourist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tourist tourist = service.register(form);

        assertEquals(Role.TOURIST, tourist.getRole());
        assertEquals("juan@mail.com", tourist.getEmail());
        assertEquals("hashed", tourist.getPasswordHash());
        verify(eventPublisher).publishEvent(
                new TouristRegisteredEvent(new MailRecipient("juan@mail.com", "Juan"), "juangomez"));
    }

    @Test
    void rejectsExistingEmail() {
        when(userRepository.existsByEmailIgnoreCase("juan@mail.com")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("email"));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
