package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.dto.ParticipantForm;
import uy.edu.um.luminalabs.dto.ReservationForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.exceptions.ReservationNotPossibleException;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-01 pasos 1 a 6 y CU-13
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final Long TOURIST_ID = 3L;
    private static final Long SLOT_ID = 30L;

    @Mock
    private SlotRepository slotRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;

    private ReservationService service;

    private Tourist tourist;
    private Business business;
    private Activity activity;
    private Slot slot;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ReservationService(slotRepository, reservationRepository, userRepository, clock);

        tourist = Tourist.builder().firstName("Ana").lastName("Pérez").birthDate(LocalDate.of(1990, 5, 10)).build();
        tourist.setId(TOURIST_ID);
        business = new Business();
        business.approve(NOW);
        activity = new Activity();
        activity.setId(5L);
        activity.setBusiness(business);
        activity.setBookingCutoffMinutes(60);
        activity.setMinimumAge(12);
        slot = new Slot(activity, START, new BigDecimal("1500.00"), 4);
    }

    private ReservationForm form(LocalDate... birthDates) {
        ReservationForm form = new ReservationForm();
        form.setSlotId(SLOT_ID);
        for (LocalDate birthDate : birthDates) {
            form.getParticipants().add(new ParticipantForm(" Ana ", "Pérez", birthDate));
        }
        return form;
    }

    private void slotExists() {
        when(slotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
    }

    @Test
    void createsPendingReservationAndTakesSeats() {
        slotExists();
        when(userRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation reservation = service.create(TOURIST_ID, form(LocalDate.of(1990, 5, 10), LocalDate.of(1992, 1, 1)));

        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.getStatus());
        assertSame(tourist, reservation.getTourist());
        assertEquals(2, reservation.getParticipantCount());
        assertEquals("Ana", reservation.getParticipants().getFirst().getFirstName(), "los nombres se guardan sin espacios");
        assertEquals(new BigDecimal("3000.00"), reservation.getTotalAmount());
        assertEquals(2, slot.getAvailability());
        assertEquals(NOW.plusHours(2), reservation.getPaymentDeadline());
    }

    // 3a: la edad se mide el dia de la actividad. Cumple 12 el 31/10, un dia antes de la actividad.
    @Test
    void ageIsCheckedOnTheActivityDate() {
        slotExists();
        when(userRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.create(TOURIST_ID, form(LocalDate.of(2014, 10, 31))));
    }

    @Test
    void rejectsParticipantBelowMinimumAge() {
        slotExists();

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.create(TOURIST_ID, form(LocalDate.of(1990, 1, 1), LocalDate.of(2014, 11, 2))));

        assertEquals(java.util.Set.of("participants[1].birthDate"), exception.getFieldErrors().keySet());
        assertEquals(4, slot.getAvailability(), "no se tomaron lugares");
        verify(reservationRepository, never()).save(any());
    }

    // 4a
    @Test
    void rejectsMoreParticipantsThanAvailability() {
        slotExists();
        LocalDate adult = LocalDate.of(1990, 1, 1);

        ReservationNotPossibleException exception = assertThrows(ReservationNotPossibleException.class,
                () -> service.create(TOURIST_ID, form(adult, adult, adult, adult, adult)));

        assertTrue(exception.getMessage().contains("quedan 4 lugares"));
        assertEquals(5L, exception.getActivityId(), "para volver a la actividad y ofrecer otros horarios");
    }

    // 5a: con 60 minutos de plazo, a las 9:30 ya no se puede reservar el slot de las 10:00
    @Test
    void rejectsReservationAfterBookingCutoff() {
        slot = new Slot(activity, NOW.plusMinutes(30), new BigDecimal("1500.00"), 4);
        slotExists();

        ReservationNotPossibleException exception = assertThrows(ReservationNotPossibleException.class,
                () -> service.create(TOURIST_ID, form(LocalDate.of(1990, 1, 1))));

        assertTrue(exception.getMessage().contains("60 minutos"));
    }

    @Test
    void rejectsCancelledSlotAndSuspendedBusiness() {
        slotExists();
        slot.cancel(NOW);
        assertThrows(ReservationNotPossibleException.class, () -> service.create(TOURIST_ID, form(LocalDate.of(1990, 1, 1))));

        slot = new Slot(activity, START, BigDecimal.TEN, 4);
        slotExists();
        business.suspend("Motivo", NOW);
        assertThrows(ReservationNotPossibleException.class, () -> service.create(TOURIST_ID, form(LocalDate.of(1990, 1, 1))));
    }

    @Test
    void newFormPrefillsTheTouristAsFirstParticipant() {
        when(userRepository.findById(TOURIST_ID)).thenReturn(Optional.of(tourist));

        ReservationForm form = service.newForm(TOURIST_ID, SLOT_ID, 3);

        assertEquals(3, form.getParticipants().size());
        assertEquals("Ana", form.getParticipants().getFirst().getFirstName());
        assertEquals(LocalDate.of(1990, 5, 10), form.getParticipants().getFirst().getBirthDate());
        assertNull(form.getParticipants().get(1).getFirstName());
    }

    // RNF-10: la reserva de otro turista no se puede ver
    @Test
    void otherTouristsReservationIsNotFound() {
        Reservation reservation = new Reservation(tourist, slot, List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1))), NOW);
        when(reservationRepository.findWithDetailsById(9L)).thenReturn(Optional.of(reservation));

        assertSame(reservation, service.findOwn(TOURIST_ID, 9L));
        assertThrows(ReservationNotFoundException.class, () -> service.findOwn(99L, 9L));
    }

    private Reservation reservationAt(LocalDateTime start, LocalDateTime createdAt) {
        return new Reservation(tourist, new Slot(activity, start, new BigDecimal("1500.00"), 4),
                List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1))), createdAt);
    }

    // CU-13 paso 2: pendientes de pago aparte, confirmadas en proximas y el resto en el historial
    @Test
    void overviewSeparatesPendingUpcomingAndHistory() {
        Reservation pending = reservationAt(START, NOW.minusMinutes(30));
        Reservation confirmed = reservationAt(START.plusDays(3), NOW);
        confirmed.confirm();
        Reservation cancelled = reservationAt(START.plusDays(5), NOW);
        cancelled.cancel();
        Reservation olderFinished = reservationAt(NOW.minusDays(20), NOW.minusDays(25));
        olderFinished.confirm();
        olderFinished.finish();
        Reservation finished = reservationAt(NOW.minusDays(2), NOW.minusDays(5));
        finished.confirm();
        finished.finish();
        // El repositorio las devuelve ordenadas por fecha del horario
        when(reservationRepository.findByTourist_IdOrderBySlot_StartTimeAsc(TOURIST_ID))
                .thenReturn(List.of(olderFinished, finished, pending, confirmed, cancelled));

        ReservationService.TouristReservations overview = service.findOverview(TOURIST_ID);

        assertEquals(List.of(pending), overview.pendingPayment());
        assertEquals(List.of(confirmed), overview.upcoming());
        assertEquals(List.of(cancelled, finished, olderFinished), overview.history(), "la mas reciente primero");
        assertEquals(2, overview.finishedCount());
        assertEquals(90, overview.minutesToPay(pending));
        assertEquals(18, overview.daysUntil(confirmed));
        assertFalse(overview.isEmpty());
    }

    @Test
    void minutesToPayIsZeroOnceTheDeadlinePassed() {
        Reservation late = reservationAt(START, NOW.minusHours(3));
        when(reservationRepository.findByTourist_IdOrderBySlot_StartTimeAsc(TOURIST_ID)).thenReturn(List.of(late));

        ReservationService.TouristReservations overview = service.findOverview(TOURIST_ID);

        assertEquals(List.of(late), overview.pendingPayment());
        assertEquals(0, overview.minutesToPay(late));
    }
}
