package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private Slot slot;

    @BeforeEach
    void setUp() {
        slot = new Slot(new Activity(), START, new BigDecimal("1500.00"), 10);
    }

    private static List<Participant> participants(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Participant("Nombre" + i, "Apellido", LocalDate.of(1990, 1, 1)))
                .toList();
    }

    @Test
    void newReservationIsPendingAndTakesSeats() {
        Reservation reservation = new Reservation(new Tourist(), slot, participants(3), NOW);

        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.getStatus());
        assertEquals(3, reservation.getParticipantCount());
        assertEquals(7, slot.getAvailability());
        assertEquals(NOW, reservation.getCreatedAt());
    }

    // RF-17: monto = cantidad de participantes x precio por persona
    @Test
    void totalIsParticipantsTimesUnitPrice() {
        Reservation reservation = new Reservation(new Tourist(), slot, participants(3), NOW);

        assertEquals(new BigDecimal("1500.00"), reservation.getUnitPrice());
        assertEquals(new BigDecimal("4500.00"), reservation.getTotalAmount());
    }

    // El precio queda congelado: si el prestador cambia el precio del slot, la reserva hecha no cambia
    @Test
    void priceChangeOnSlotDoesNotAffectExistingReservation() {
        Reservation reservation = new Reservation(new Tourist(), slot, participants(2), NOW);

        slot.update(START, new BigDecimal("2000.00"), 10, NOW);

        assertEquals(new BigDecimal("1500.00"), reservation.getUnitPrice());
        assertEquals(new BigDecimal("3000.00"), reservation.getTotalAmount());
        Reservation later = new Reservation(new Tourist(), slot, participants(2), NOW);
        assertEquals(new BigDecimal("4000.00"), later.getTotalAmount());
    }

    @Test
    void cannotReserveMoreThanAvailability() {
        assertThrows(IllegalStateException.class,
                () -> new Reservation(new Tourist(), slot, participants(11), NOW));
        assertEquals(10, slot.getAvailability());
    }

    @Test
    void cannotReserveCancelledSlot() {
        slot.cancel(NOW);

        assertThrows(IllegalStateException.class,
                () -> new Reservation(new Tourist(), slot, participants(1), NOW));
    }

    // CU-05 paso 9: al cancelar vuelven los lugares
    @Test
    void cancelReleasesSeats() {
        Reservation reservation = new Reservation(new Tourist(), slot, participants(3), NOW);

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(10, slot.getAvailability());
        assertThrows(IllegalStateException.class, reservation::cancel);
    }

    // CU-18 paso 2: al vencer tambien
    @Test
    void expireReleasesSeats() {
        Reservation reservation = new Reservation(new Tourist(), slot, participants(2), NOW);

        reservation.expire();

        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertEquals(10, slot.getAvailability());
    }

    // RF-20: termina en el inicio del slot mas la duracion; solo una confirmada pasa a terminada
    @Test
    void endTimeAndFinish() {
        slot.getActivity().setDurationMinutes(90);
        Reservation reservation = new Reservation(new Tourist(), slot, participants(1), NOW);

        assertEquals(START.plusMinutes(90), reservation.getEndTime());
        assertThrows(IllegalStateException.class, reservation::finish);
        reservation.confirm();
        reservation.finish();
        assertEquals(ReservationStatus.FINISHED, reservation.getStatus());
    }

    @Test
    void needsAtLeastOneParticipant() {
        assertThrows(IllegalArgumentException.class,
                () -> new Reservation(new Tourist(), slot, List.of(), NOW));
    }
}
