package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.BusinessForm;
import uy.edu.um.luminalabs.dto.ProviderRegistrationForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderRegistrationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

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
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ProviderRegistrationService(userRepository, businessRepository, locationRepository,
                passwordEncoder, new AccountValidator(userRepository), eventPublisher, clock);

        form = new ProviderRegistrationForm();
        BusinessForm business = form.getBusiness();
        business.setBusinessType(BusinessType.INDIVIDUAL);
        business.setLegalName("Cabalgatas del Este");
        business.setIdentifier("1.234.567-8");
        business.setDescription("Cabalgatas por la sierra");
        business.setLocationIds(List.of(1L));
        business.setContactEmail("contacto@cabalgatas.uy");
        business.setContactPhone("099 123 456");
        business.setBankName("BROU");
        business.setBankAccountNumber("001234567");
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

    private static Provider provider(Long id) {
        Provider provider = Provider.builder().firstName("Ana").username("anaperez").email("ana@mail.com")
                .passwordHash("hashed").build();
        provider.setId(id);
        return provider;
    }

    private static Business deniedBusinessOf(Provider provider) {
        Business business = new Business();
        business.setIdentifier("12345678");
        business.setLegalName("Nombre viejo");
        business.addProvider(provider);
        business.deny("Faltan datos", NOW);
        return business;
    }

    private void saveReturnsArgument() {
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersPendingBusinessWithItsProvider() {
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(passwordEncoder.encode("secreta123")).thenReturn("hashed");
        when(userRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        saveReturnsArgument();

        Business business = service.register(form);

        assertEquals(BusinessStatus.PENDING, business.getStatus());
        assertEquals("12345678", business.getIdentifier());
        assertEquals(NOW, business.getCreatedAt());
        assertTrue(business.getLocations().contains(location));
        Provider provider = business.getProviders().iterator().next();
        assertEquals("ana@mail.com", provider.getEmail());
        assertEquals("hashed", provider.getPasswordHash());
        verify(eventPublisher).publishEvent(new BusinessRegisteredEvent(
                new MailRecipient("ana@mail.com", "Ana"), "Cabalgatas del Este"));
    }

    @Test
    void rejectsDuplicatedIdentifierEmailAndUsername() {
        Business approved = new Business();
        approved.approve(NOW);
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.findByIdentifier("12345678")).thenReturn(Optional.of(approved));
        when(userRepository.existsByUsernameIgnoreCase("anaperez")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("ana@mail.com")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertEquals(Set.of("business.identifier", "username", "email"), exception.getFieldErrors().keySet());
        verify(businessRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCompanyWithoutTwelveDigitRut() {
        form.getBusiness().setBusinessType(BusinessType.COMPANY);
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("business.identifier"));
    }

    @Test
    void rejectsDifferentPasswords() {
        form.setConfirmPassword("otra-clave");
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("confirmPassword"));
    }

    // CU-02 5c: el prestador del emprendimiento denegado se identifica con su cuenta y lo vuelve a postular
    @Test
    void resubmitsDeniedBusinessWithItsOwnAccount() {
        Provider owner = provider(7L);
        Business denied = deniedBusinessOf(owner);
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.findByIdentifier("12345678")).thenReturn(Optional.of(denied));
        when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("secreta123", "hashed")).thenReturn(true);
        saveReturnsArgument();

        Business business = service.register(form);

        assertSame(denied, business);
        assertEquals(BusinessStatus.PENDING, business.getStatus());
        assertEquals("Cabalgatas del Este", business.getLegalName());
        assertNull(business.getStatusReason());
        assertEquals(1, business.getProviders().size());
        verify(userRepository, never()).save(any());
    }

    // Sin la contrasena correcta no se puede tomar un emprendimiento denegado ajeno
    @Test
    void rejectsResubmissionWithWrongPassword() {
        Provider owner = provider(7L);
        Business denied = deniedBusinessOf(owner);
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.findByIdentifier("12345678")).thenReturn(Optional.of(denied));
        when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("secreta123", "hashed")).thenReturn(false);

        FieldValidationException exception = assertThrows(FieldValidationException.class, () -> service.register(form));

        assertTrue(exception.getFieldErrors().containsKey("business.identifier"));
        assertEquals(BusinessStatus.DENIED, denied.getStatus());
        verify(businessRepository, never()).save(any());
    }

    // CU-02 1a: un prestador logueado agrega otro emprendimiento sin crear otra cuenta
    @Test
    void loggedInProviderRegistersAnotherBusiness() {
        Provider provider = provider(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(provider));
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.findByIdentifier("12345678")).thenReturn(Optional.empty());
        saveReturnsArgument();

        Business business = service.registerForProvider(7L, form.getBusiness());

        assertEquals(BusinessStatus.PENDING, business.getStatus());
        assertTrue(business.hasProvider(7L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loggedInProviderCannotResubmitSomeoneElsesDeniedBusiness() {
        Business denied = deniedBusinessOf(provider(8L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(provider(7L)));
        when(locationRepository.findAllById(List.of(1L))).thenReturn(List.of(location));
        when(businessRepository.findByIdentifier("12345678")).thenReturn(Optional.of(denied));

        assertThrows(FieldValidationException.class, () -> service.registerForProvider(7L, form.getBusiness()));
        assertEquals(BusinessStatus.DENIED, denied.getStatus());
    }
}
