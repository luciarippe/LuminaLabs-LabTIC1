package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ActivityTest {

    @Test
    void durationLabel() {
        Activity activity = new Activity();

        activity.setDurationMinutes(45);
        assertEquals("45 min", activity.getDurationLabel());
        activity.setDurationMinutes(180);
        assertEquals("3 h", activity.getDurationLabel());
        activity.setDurationMinutes(150);
        assertEquals("2 h 30 min", activity.getDurationLabel());
    }

    @Test
    void newSlotStartsWithFullAvailability() {
        Slot slot = new Slot(new Activity(), LocalDateTime.of(2026, 11, 1, 10, 0), BigDecimal.valueOf(1500), 12);

        assertEquals(12, slot.getAvailability());
        assertEquals(SlotStatus.ACTIVE, slot.getStatus());
    }

    @Test
    void slotRejectsNonPositivePriceOrCapacity() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> new Slot(new Activity(), start, BigDecimal.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new Slot(new Activity(), start, BigDecimal.TEN, 0));
    }

    // RF-10: se puede reservar hasta "bookingCutoffMinutes" antes del inicio
    @Test
    void slotIsBookableOnlyBeforeCutoff() {
        Activity activity = new Activity();
        activity.setBookingCutoffMinutes(120);
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        Slot slot = new Slot(activity, start, BigDecimal.valueOf(1500), 12);

        assertTrue(slot.isBookableAt(start.minusHours(3)));
        assertFalse(slot.isBookableAt(start.minusHours(2)));
        assertFalse(slot.isBookableAt(start.minusMinutes(30)));
    }

    @Test
    void zeroCutoffAllowsBookingUntilStart() {
        Activity activity = new Activity();
        activity.setBookingCutoffMinutes(0);
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        Slot slot = new Slot(activity, start, BigDecimal.valueOf(1500), 12);

        assertTrue(slot.isBookableAt(start.minusMinutes(1)));
        assertFalse(slot.isBookableAt(start));
    }
}
