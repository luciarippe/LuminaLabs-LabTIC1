package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.repositories.CancellationRepository;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;
import uy.edu.um.luminalabs.services.ProviderReservationService.Filters;
import uy.edu.um.luminalabs.services.ProviderReservationService.StatusFilter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// CU-09: reservas recibidas por el prestador
@ExtendWith(MockitoExtension.class)
class ProviderReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long PROVIDER_ID = 7L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CancellationRepository cancellationRepository;

    private ProviderReservationService service;
    private Activity activity;

    @BeforeEach
    void setUp() {
        service = new ProviderReservationService(reservationRepository, slotRepository, paymentRepository,
                cancellationRepository, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        Business business = new Business();
        business.addProvider(provider);
        business.approve(NOW);
        activity = new Activity();
        activity.setBusiness(business);
    }

    private static Slot slot(Activity activity, LocalDateTime start, int capacity) {
        return new Slot(activity, start, new BigDecimal("1500.00"), capacity);
    }

    private static Reservation reservation(Slot slot, int participants) {
        return new Reservation(new Tourist(), slot, IntStream.range(0, participants)
                .mapToObj(i -> new Participant("Nombre" + i, "Apellido" + i, LocalDate.of(1990, 1, 1)))
                .toList(), NOW);
    }

    private static Filters defaults() {
        return new Filters(null, null, null, null, null);
    }

    @Test
    void groupsReservationsBySlotKeepingDateOrder() {
        Slot tonight = slot(activity, NOW.withHour(19), 10);
        Slot nextWeek = slot(activity, NOW.plusDays(7), 12);
        Reservation first = reservation(tonight, 2);
        Reservation second = reservation(tonight, 3);
        second.confirm();
        Reservation third = reservation(nextWeek, 4);
        when(reservationRepository.findForProvider(eq(PROVIDER_ID), isNull(), isNull(), any(), any(), anyCollection()))
                .thenReturn(List.of(first, second, third));

        List<ProviderReservationService.SlotReservations> result = service.find(PROVIDER_ID, defaults());

        assertEquals(2, result.size());
        ProviderReservationService.SlotReservations today = result.getFirst();
        assertSame(tonight, today.slot());
        assertEquals(List.of(first, second), today.reservations());
        assertEquals(0, today.daysLeft());
        assertEquals(3, today.confirmedSeats());
        assertEquals(2, today.pendingSeats());
        assertEquals(50, today.occupancyPercent());
        assertEquals(7, result.get(1).daysLeft());
    }

    // Sin filtros: las vigentes desde ahora. Con "Terminadas" y sin fecha: tambien los horarios pasados.
    @Test
    void periodAndStatusesFollowTheFilters() {
        service.find(PROVIDER_ID, defaults());
        verify(reservationRepository).findForProvider(eq(PROVIDER_ID), isNull(), isNull(), eq(NOW), any(),
                eq(List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)));

        service.find(PROVIDER_ID, new Filters(4L, 9L, null, null, StatusFilter.FINISHED));
        verify(reservationRepository).findForProvider(eq(PROVIDER_ID), eq(4L), eq(9L),
                eq(LocalDateTime.of(2000, 1, 1, 0, 0)), any(), eq(List.of(ReservationStatus.FINISHED)));

        // Fechas al reves: se dan vuelta; "hasta" incluye todo ese dia
        service.find(PROVIDER_ID, new Filters(null, null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), StatusFilter.ALL));
        verify(reservationRepository).findForProvider(eq(PROVIDER_ID), isNull(), isNull(),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0)),
                eq(List.of(ReservationStatus.values())));
    }

    @Test
    void defaultFiltersAreRecognized() {
        assertTrue(defaults().isDefault());
        assertEquals(StatusFilter.ACTIVE, defaults().status());
        assertFalse(new Filters(1L, null, null, null, null).isDefault());
    }

    // Paso 6: el estado del pago
    @Test
    void reservationDetailShowsPaymentStatus() {
        Reservation reservation = reservation(slot(activity, NOW.plusDays(3), 10), 2);
        when(reservationRepository.findWithDetailsById(5L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservation_IdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

        ProviderReservationService.ReservationDetail detail = service.findReservation(PROVIDER_ID, 5L);

        assertEquals("Todavía no pagó", detail.paymentSummary().label());
        assertSame(reservation, detail.reservation());
    }

    // La reserva de otro prestador no existe para el (404)
    @Test
    void otherProvidersReservationIsNotFound() {
        Reservation reservation = reservation(slot(activity, NOW.plusDays(3), 10), 2);
        when(reservationRepository.findWithDetailsById(5L)).thenReturn(Optional.of(reservation));

        assertThrows(ReservationNotFoundException.class, () -> service.findReservation(99L, 5L));
    }

    // 6a: la lista consolidada solo tiene a los confirmados, numerados de corrido; los pendientes se cuentan aparte
    @Test
    void slotParticipantsListsConfirmedOnes() {
        Slot slot = slot(activity, NOW.plusDays(2), 10);
        Reservation confirmedA = reservation(slot, 2);
        confirmedA.confirm();
        Reservation confirmedB = reservation(slot, 3);
        confirmedB.confirm();
        Reservation pending = reservation(slot, 4);
        when(slotRepository.findWithActivityById(3L)).thenReturn(Optional.of(slot));
        when(reservationRepository.findWithParticipantsBySlot_IdAndStatusInOrderByCreatedAtAsc(eq(3L), anyCollection()))
                .thenReturn(List.of(confirmedA, pending, confirmedB));

        ProviderReservationService.SlotParticipants list = service.findSlotParticipants(PROVIDER_ID, 3L);

        assertEquals(List.of(confirmedA, confirmedB), list.confirmed());
        assertEquals(5, list.confirmedSeats());
        assertEquals(4, list.pendingSeats());
        List<ProviderReservationService.ParticipantRow> rows = list.rows();
        assertEquals(5, rows.size());
        assertEquals(List.of(1, 2, 3, 4, 5), rows.stream().map(ProviderReservationService.ParticipantRow::number).toList());
        assertTrue(rows.get(0).firstOfReservation());
        assertFalse(rows.get(1).firstOfReservation());
        assertTrue(rows.get(2).firstOfReservation());
    }

    @Test
    void participantAgeIsOnTheActivityDate() {
        Participant participant = new Participant("Ana", "Pérez", LocalDate.of(2014, 11, 2));

        assertEquals(11, participant.ageOn(LocalDate.of(2026, 11, 1)));
        assertEquals(12, participant.ageOn(LocalDate.of(2026, 11, 2)));
    }
}
