package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// RF-23 y CU-08: reglas de una valoracion
class RatingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    private Activity activity;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        activity = new Activity();
        Tourist tourist = Tourist.builder().firstName("Ana").lastName("pérez").birthDate(LocalDate.of(1990, 5, 10)).build();
        Slot slot = new Slot(activity, NOW.minusDays(3), new BigDecimal("1500.00"), 10);
        reservation = new Reservation(tourist, slot, List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 5, 10))),
                NOW.minusDays(5));
    }

    private void finish() {
        reservation.confirm();
        reservation.finish();
    }

    @Test
    void finishedReservationCanBeRated() {
        finish();

        Rating rating = new Rating(reservation, 5, "  Excelente  ", " Muy buena experiencia. ", NOW);

        assertEquals(5, rating.getStars());
        assertEquals("Excelente", rating.getTitle());
        assertEquals("Muy buena experiencia.", rating.getComment());
        assertSame(activity, rating.getActivity());
        assertEquals(NOW, rating.getCreatedAt());
    }

    // CU-08 1a: pendiente, confirmada (todavia no fue) o cancelada no se pueden valorar
    @Test
    void onlyFinishedReservationsCanBeRated() {
        assertThrows(IllegalStateException.class, () -> new Rating(reservation, 4, "Título", "Texto", NOW));
        reservation.confirm();
        assertThrows(IllegalStateException.class, () -> new Rating(reservation, 4, "Título", "Texto", NOW));
        reservation.cancel();
        assertThrows(IllegalStateException.class, () -> new Rating(reservation, 4, "Título", "Texto", NOW));
    }

    @Test
    void starsMustBeBetweenOneAndFive() {
        finish();

        assertThrows(IllegalArgumentException.class, () -> new Rating(reservation, 0, "Título", "Texto", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Rating(reservation, 6, "Título", "Texto", NOW));
        assertDoesNotThrow(() -> new Rating(reservation, 1, "Título", "Texto", NOW));
    }

    @Test
    void titleAndCommentAreRequired() {
        finish();

        assertThrows(IllegalArgumentException.class, () -> new Rating(reservation, 4, " ", "Texto", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Rating(reservation, 4, "Título", null, NOW));
    }

    @Test
    void authorIsFirstNameAndLastNameInitial() {
        finish();

        assertEquals("Ana p.", new Rating(reservation, 4, "Título", "Texto", NOW).getAuthorName());
    }
}
