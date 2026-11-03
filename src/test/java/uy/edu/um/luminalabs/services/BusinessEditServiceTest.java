package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.dto.BusinessEditForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Un prestador corrige los datos de su emprendimiento
@ExtendWith(MockitoExtension.class)
class BusinessEditServiceTest {

    private static final Long BUSINESS_ID = 4L;
    private static final Long PROVIDER_ID = 1L;

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ActivityRepository activityRepository;

    private BusinessEditService service;
    private Business business;
    private Location laPaloma;
    private Location caboPolonio;
    private Location rocha;

    @BeforeEach
    void setUp() {
        service = new BusinessEditService(new SocialLinksService(businessRepository), locationRepository, activityRepository);
        laPaloma = location(26L, "La Paloma");
        caboPolonio = location(27L, "Cabo Polonio");
        rocha = location(25L, "Rocha");
        Provider ana = Provider.builder().firstName("Ana").lastName("Pérez").email("ana@correo.uy").username("ana")
                .birthDate(LocalDate.of(1990, 1, 1)).build();
        ana.setId(PROVIDER_ID);
        business = new Business();
        business.setId(BUSINESS_ID);
        business.setType(BusinessType.COMPANY);
        business.setIdentifier("217845390018");
        business.setLegalName("Costa Este");
        business.setBank(Bank.BROU);
        business.setBankAccountNumber("00123456700001");
        business.getLocations().addAll(List.of(laPaloma, caboPolonio));
        business.addProvider(ana);
        business.approve(LocalDateTime.of(2026, 10, 1, 12, 0));
        lenient().when(businessRepository.findWithDetailsById(BUSINESS_ID)).thenReturn(Optional.of(business));
    }

    private static Location location(Long id, String name) {
        Location location = new Location(name, Department.ROCHA);
        location.setId(id);
        return location;
    }

    private static Activity activityIn(Location location) {
        Activity activity = new Activity();
        activity.setLocation(location);
        return activity;
    }

    private static BusinessEditForm form(List<Long> locationIds) {
        BusinessEditForm form = new BusinessEditForm();
        form.setLegalName("  Costa Este Experiencias ");
        form.setDescription("Excursiones por la costa de Rocha.");
        form.setLocationIds(locationIds);
        form.setContactEmail("Hola@CostaEste.com.uy");
        form.setContactPhone("099 412 380");
        form.setBank(Bank.ITAU);
        form.setBankAccountNumber("0123456");
        return form;
    }

    @Test
    void updatesTheDataButNotTheTypeTheIdentifierOrTheStatus() {
        when(locationRepository.findAllById(List.of(26L, 25L))).thenReturn(List.of(laPaloma, rocha));
        when(activityRepository.findByBusiness_Id(BUSINESS_ID)).thenReturn(List.of(activityIn(laPaloma)));

        service.update(PROVIDER_ID, BUSINESS_ID, form(List.of(26L, 25L)));

        assertEquals("Costa Este Experiencias", business.getLegalName());
        assertEquals("hola@costaeste.com.uy", business.getContactEmail());
        assertEquals(Bank.ITAU, business.getBank());
        assertEquals("0123456", business.getBankAccountNumber());
        assertEquals(java.util.Set.of(laPaloma, rocha), business.getLocations());
        assertEquals("217845390018", business.getIdentifier());
        assertEquals(BusinessType.COMPANY, business.getType());
        assertEquals(BusinessStatus.APPROVED, business.getStatus());
    }

    @Test
    void aLocationUsedByAnActivityCannotBeRemoved() {
        when(locationRepository.findAllById(List.of(26L))).thenReturn(List.of(laPaloma));
        when(activityRepository.findByBusiness_Id(BUSINESS_ID)).thenReturn(List.of(activityIn(caboPolonio)));

        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> service.update(PROVIDER_ID, BUSINESS_ID, form(List.of(26L))));

        assertTrue(error.getFieldErrors().get("locationIds").contains("Cabo Polonio"));
        assertEquals("Costa Este", business.getLegalName());
    }

    @Test
    void theAccountNumberHasToMatchTheBank() {
        when(locationRepository.findAllById(List.of(26L, 27L))).thenReturn(List.of(laPaloma, caboPolonio));
        BusinessEditForm form = form(List.of(26L, 27L));
        form.setBank(Bank.BROU);
        form.setBankAccountNumber("123");

        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> service.update(PROVIDER_ID, BUSINESS_ID, form));

        assertTrue(error.getFieldErrors().containsKey("bankAccountNumber"));
        assertEquals("00123456700001", business.getBankAccountNumber());
    }

    @Test
    void onlyItsProvidersCanEditItAndNotWhileDenied() {
        assertThrows(BusinessNotFoundException.class, () -> service.findEditableBusiness(99L, BUSINESS_ID));

        Business denied = new Business();
        denied.setId(5L);
        denied.setLegalName("Excursiones Express");
        denied.addProvider(business.getProviders().iterator().next());
        denied.deny("Datos incorrectos", LocalDateTime.of(2026, 10, 30, 12, 0));
        when(businessRepository.findWithDetailsById(5L)).thenReturn(Optional.of(denied));

        assertThrows(IllegalStateException.class, () -> service.findEditableBusiness(PROVIDER_ID, 5L));
    }
}
