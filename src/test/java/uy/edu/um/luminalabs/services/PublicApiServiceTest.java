package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.InvalidApiParameterException;
import uy.edu.um.luminalabs.repositories.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// CU-17: reglas de la API publica (validacion de parametros, solo aprobados, periodo del top)
@ExtendWith(MockitoExtension.class)
class PublicApiServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ActivityCatalogService catalogService;

    private PublicApiService service;

    @BeforeEach
    void setUp() {
        service = new PublicApiService(businessRepository, activityRepository, locationRepository, slotRepository,
                ratingRepository, catalogService, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void departmentAcceptsCodeOrNameWithoutAccents() {
        assertEquals(Department.TREINTA_Y_TRES, PublicApiService.parseDepartment("Treinta y Tres"));
        assertEquals(Department.PAYSANDU, PublicApiService.parseDepartment("paysandú"));
        assertEquals(Department.ROCHA, PublicApiService.parseDepartment(" ROCHA "));
        assertThrows(InvalidApiParameterException.class, () -> PublicApiService.parseDepartment("Narnia"));
    }

    // 2a: hay que indicar uno solo de los dos lugares, y la fecha
    @Test
    void dayNeedsDateAndExactlyOnePlace() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        assertThrows(InvalidApiParameterException.class, () -> service.findActivitiesOfDay(null, "ROCHA", null));
        assertThrows(InvalidApiParameterException.class, () -> service.findActivitiesOfDay(day, null, null));
        assertThrows(InvalidApiParameterException.class, () -> service.findActivitiesOfDay(day, "ROCHA", 3L));
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(InvalidApiParameterException.class, () -> service.findActivitiesOfDay(day, null, 99L));
    }

    // Los horarios del dia se agrupan por actividad, solo de emprendimientos aprobados y activos
    @Test
    void dayGroupsSlotsByActivity() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        Activity kayak = new Activity();
        Activity surf = new Activity();
        List<Slot> slots = List.of(
                new Slot(kayak, day.atTime(10, 0), BigDecimal.TEN, 10),
                new Slot(surf, day.atTime(11, 0), BigDecimal.TEN, 10),
                new Slot(kayak, day.atTime(17, 0), BigDecimal.TEN, 10));
        when(slotRepository.findForDayInDepartment(Department.ROCHA, day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                SlotStatus.ACTIVE, BusinessStatus.APPROVED)).thenReturn(slots);

        PublicApiService.DayActivities result = service.findActivitiesOfDay(day, "rocha", null);

        assertEquals(2, result.activities().size());
        assertSame(kayak, result.activities().getFirst().activity());
        assertEquals(2, result.activities().getFirst().slots().size());
        assertEquals(Department.ROCHA, result.department());
    }

    // c: por defecto el mes actual, y se piden 3
    @Test
    void topUsesCurrentMonthByDefault() {
        when(ratingRepository.findTopInDepartment(eq(Department.ROCHA), eq(BusinessStatus.APPROVED), any(), any(), any()))
                .thenReturn(List.of());

        PublicApiService.TopActivities top = service.findTopOfMonth("ROCHA", null, null);

        assertEquals(YearMonth.of(2026, 10), top.period());
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository).findTopInDepartment(eq(Department.ROCHA), eq(BusinessStatus.APPROVED),
                from.capture(), to.capture(), page.capture());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), to.getValue());
        assertEquals(3, page.getValue().getPageSize());
    }

    @Test
    void topRejectsInvalidMonth() {
        assertThrows(InvalidApiParameterException.class, () -> service.findTopOfMonth("ROCHA", 13, null));
        assertThrows(InvalidApiParameterException.class, () -> service.findTopOfMonth(null, 10, 2026));
    }

    // d: con o sin puntos y guiones; si no esta aprobado, no esta verificado (3b)
    @Test
    void verificationOnlyCountsApprovedBusinesses() {
        Business suspended = new Business();
        suspended.approve(NOW);
        suspended.suspend("Motivo", NOW);
        when(businessRepository.findByIdentifier("219999990011")).thenReturn(Optional.of(suspended));

        PublicApiService.Verification result = service.verify("21.999999-0011");

        assertEquals("219999990011", result.identifier());
        assertFalse(result.isVerified());
        assertThrows(InvalidApiParameterException.class, () -> service.verify(" - "));
    }

    @Test
    void searchNeedsAtLeastTwoCharacters() {
        assertThrows(InvalidApiParameterException.class, () -> service.search("a"));
        assertThrows(InvalidApiParameterException.class, () -> service.search(null));
        verifyNoInteractions(catalogService);
    }
}
