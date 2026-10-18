package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

// Reglas de un slot al modificarlo o cancelarlo (CU-04, RF-11)
class SlotTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private Slot slot;

    @BeforeEach
    void setUp() {
        slot = new Slot(new Activity(), START, BigDecimal.valueOf(1500), 10);
    }

    // Lo que hace una reserva al crearse (ver Reservation)
    private void reserve(int seats) {
        slot.reserveSeats(seats);
    }

    @Test
    void updateKeepsReservedSeats() {
        reserve(4);

        slot.update(START, BigDecimal.valueOf(1800), 12, NOW);

        assertEquals(12, slot.getMaxCapacity());
        assertEquals(8, slot.getAvailability());
        assertEquals(4, slot.getReservedSeats());
        assertEquals(BigDecimal.valueOf(1800), slot.getPrice());
    }

    @Test
    void capacityCannotGoBelowReservedSeats() {
        reserve(6);

        assertThrows(IllegalStateException.class, () -> slot.update(START, BigDecimal.TEN, 5, NOW));
        assertEquals(10, slot.getMaxCapacity());
    }

    @Test
    void capacityCanBeReducedToExactlyTheReservedSeats() {
        reserve(6);

        slot.update(START, BigDecimal.TEN, 6, NOW);

        assertTrue(slot.isSoldOut());
    }

    @Test
    void updateReportsScheduleChange() {
        assertFalse(slot.update(START, BigDecimal.TEN, 10, NOW));
        assertTrue(slot.update(START.plusHours(2), BigDecimal.TEN, 10, NOW));
        assertEquals(START.plusHours(2), slot.getStartTime());
    }

    @Test
    void cancelledSlotIsNotBookableAndCannotChange() {
        slot.cancel(NOW);

        assertEquals(SlotStatus.CANCELLED, slot.getStatus());
        assertFalse(slot.isBookableAt(NOW));
        assertThrows(IllegalStateException.class, () -> slot.cancel(NOW));
        assertThrows(IllegalStateException.class, () -> slot.update(START, BigDecimal.TEN, 10, NOW));
    }

    // La restriccion unica esta sobre activeStartTime: se libera al cancelar (opcion 1)
    @Test
    void activeStartTimeFollowsStatus() {
        assertEquals(START, slot.getActiveStartTime());

        slot.update(START.plusHours(1), BigDecimal.TEN, 10, NOW);
        assertEquals(START.plusHours(1), slot.getActiveStartTime());

        slot.cancel(NOW);
        assertNull(slot.getActiveStartTime());
        assertEquals(START.plusHours(1), slot.getStartTime(), "el horario original queda como historial");
    }

    @Test
    void cannotReserveMoreSeatsThanAvailable() {
        reserve(8);

        assertThrows(IllegalStateException.class, () -> slot.reserveSeats(3));
        assertEquals(2, slot.getAvailability());
    }

    @Test
    void releasedSeatsReturnToAvailability() {
        reserve(4);

        slot.releaseSeats(4);

        assertEquals(10, slot.getAvailability());
        assertThrows(IllegalArgumentException.class, () -> slot.releaseSeats(1));
    }

    @Test
    void startedSlotCannotChange() {
        assertThrows(IllegalStateException.class, () -> slot.cancel(START));
        assertThrows(IllegalStateException.class, () -> slot.update(START, BigDecimal.TEN, 10, START.plusMinutes(5)));
    }
}
