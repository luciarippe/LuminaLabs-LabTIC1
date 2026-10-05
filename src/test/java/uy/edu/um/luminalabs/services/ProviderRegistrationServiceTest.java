package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.ProviderRegistrationForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProviderRegistrationService service;

    private ProviderRegistrationForm form;
    private Location location;

    @BeforeEach
    void setUp() {
        service = new ProviderRegistrationService(userRepository, businessRepository, locationRepository,
                passwordEncoder, new AccountValidator(userRepository), eventPublisher);

        form = new ProviderRegistrationForm();
        form.setBusinessType(BusinessType.INDIVIDUAL);
        form.setLegalName("Cabalgatas del Este");
        form.setIdentifier("1.234.567-8");
        form.setDescription("Cabalgatas por la sierra");
        form.setLocationIds(List.of(1L));
        form.setContactEmail("contacto@cabalgatas.uy");
        form.setContactPhone("099 123 456");
        form.setBankName("BROU");
        form.setBankAccountNumber("001234567");
        form.setFirstName("Ana");
        form.setLastName("Pérez");
        form.setBirthDate(LocalDate.of(1990, 5, 10));
        form.setUsername("anaperez");
        form.setEmail("Ana@Mail.com");
        form.setPassword("secreta123");
        form.setConfirmPassword("secreta123");

        location = new Location("Minas", Department.LAVALLEJA);
        location.setId(1L);
    }

    @Test
    void registersPendingBusinessWithItsProvider() {
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(passwordEncoder.encode("secreta123")).thenReturn("hashed");
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Business business = service.register(form);

        assertEquals(BusinessStatus.PENDING, business.getStatus());
        assertEquals("12345678", business.getIdentifier());
        assertTrue(business.getLocations().contains(location));
        Provider provider = business.getProviders().iterator().next();
        assertEquals("ana@mail.com", provider.getEmail());
        assertEquals("hashed", provider.getPasswordHash());
        verify(userRepository).save(provider);
        verify(eventPublisher).publishEvent(new BusinessRegisteredEvent(
                new MailRecipient("ana@mail.com", "Ana"), "Cabalgatas del Este"));
    }

    @Test
    void rejectsDuplicatedIdentifierEmailAndUsername() {
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.existsByIdentifier("12345678")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("anaperez")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("ana@mail.com")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertEquals(java.util.Set.of("identifier", "username", "email"), exception.getFieldErrors().keySet());
        verify(businessRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCompanyWithoutTwelveDigitRut() {
        form.setBusinessType(BusinessType.COMPANY);
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("identifier"));
    }

    @Test
    void rejectsDifferentPasswords() {
        form.setConfirmPassword("otra-clave");
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("confirmPassword"));
    }
}
