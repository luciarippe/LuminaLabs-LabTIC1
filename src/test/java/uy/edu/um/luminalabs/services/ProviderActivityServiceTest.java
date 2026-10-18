package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.dto.ActivityForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.ActivityNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-03: publicar y modificar actividades
@ExtendWith(MockitoExtension.class)
class ProviderActivityServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long PROVIDER_ID = 7L;

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ActivityCategoryRepository categoryRepository;

    private ProviderActivityService service;

    private Business business;
    private Location location;
    private ActivityCategory category;
    private ActivityForm form;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ProviderActivityService(activityRepository, businessRepository, locationRepository,
                categoryRepository, clock);

        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        business = new Business();
        business.setId(1L);
        business.setLegalName("Sierras de Lavalleja");
        business.addProvider(provider);
        business.approve(NOW);

        location = new Location("Minas", Department.LAVALLEJA);
        location.setId(10L);
        category = new ActivityCategory("Trekking", null);
        category.setId(20L);

        form = new ActivityForm();
        form.setBusinessId(1L);
        form.setName(" Trekking al Cerro Arequita ");
        form.setDescription("Ascenso al cerro");
        form.setLocationId(10L);
        form.setAddress("Parque Salus");
        form.setDurationMinutes(180);
        form.setLanguage("Español");
        form.setEquipment("   ");
        form.setCategoryIds(List.of(20L));
        form.setBookingCutoffMinutes(60);
        form.setCancellationDeadlineHours(24);
        form.setCancellationFeePercentage(10);
    }

    private void lookupsSucceed() {
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(categoryRepository.findAllById(List.of(20L))).thenReturn(List.of(category));
    }

    @Test
    void createsActivityInOwnApprovedBusiness() {
        when(businessRepository.findWithDetailsById(1L)).thenReturn(Optional.of(business));
        lookupsSucceed();
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Activity activity = service.create(PROVIDER_ID, form);

        assertSame(business, activity.getBusiness());
        assertEquals("Trekking al Cerro Arequita", activity.getName());
        assertEquals(location, activity.getLocation());
        assertTrue(activity.getCategories().contains(category));
        assertNull(activity.getEquipment(), "un campo opcional en blanco se guarda como null");
        assertEquals(10, activity.getCancellationFeePercentage());
        assertEquals(NOW, activity.getCreatedAt());
    }

    @Test
    void cannotCreateActivityInSomeoneElsesBusiness() {
        when(businessRepository.findWithDetailsById(1L)).thenReturn(Optional.of(business));
        lookupsSucceed();

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.create(99L, form));

        assertTrue(exception.getFieldErrors().containsKey("businessId"));
        verify(activityRepository, never()).save(any());
    }

    // RF-06: un emprendimiento suspendido no puede publicar
    @Test
    void cannotCreateActivityInSuspendedBusiness() {
        business.suspend("Motivo", NOW);
        when(businessRepository.findWithDetailsById(1L)).thenReturn(Optional.of(business));
        lookupsSucceed();

        assertThrows(FieldValidationException.class, () -> service.create(PROVIDER_ID, form));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownCategory() {
        when(businessRepository.findWithDetailsById(1L)).thenReturn(Optional.of(business));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(categoryRepository.findAllById(List.of(20L))).thenReturn(List.of());

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.create(PROVIDER_ID, form));

        assertTrue(exception.getFieldErrors().containsKey("categoryIds"));
    }

    // RNF-10: la actividad de otro prestador se trata como inexistente
    @Test
    void otherProvidersActivityIsNotFound() {
        Activity activity = new Activity();
        activity.setBusiness(business);
        when(activityRepository.findWithBusinessById(5L)).thenReturn(Optional.of(activity));

        assertThrows(ActivityNotFoundException.class, () -> service.findOwnActivity(99L, 5L));
        assertSame(activity, service.findOwnActivity(PROVIDER_ID, 5L));
    }

    @Test
    void cannotUpdateActivityOfSuspendedBusiness() {
        Activity activity = new Activity();
        activity.setBusiness(business);
        business.suspend("Motivo", NOW);
        when(activityRepository.findWithBusinessById(5L)).thenReturn(Optional.of(activity));

        assertThrows(IllegalStateException.class, () -> service.update(PROVIDER_ID, 5L, form));
    }
}
