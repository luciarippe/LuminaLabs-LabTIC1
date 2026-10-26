package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.dto.RatingForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.repositories.RatingRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-08: valorar una actividad
@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long TOURIST_ID = 3L;
    private static final Long RESERVATION_ID = 9L;

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ReservationService reservationService;

    private RatingService service;
    private Reservation reservation;
    private RatingForm form;

    @BeforeEach
    void setUp() {
        service = new RatingService(ratingRepository, reservationService, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        Tourist tourist = Tourist.builder().firstName("Ana").lastName("Pérez").birthDate(LocalDate.of(1990, 5, 10)).build();
        Slot slot = new Slot(new Activity(), NOW.minusDays(2), new BigDecimal("1500.00"), 10);
        reservation = new Reservation(tourist, slot, List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 5, 10))),
                NOW.minusDays(4));
        form = new RatingForm();
        form.setStars(4);
        form.setTitle("Muy linda");
        form.setComment("La guía explicó todo muy bien.");
    }

    private void ownReservation() {
        when(reservationService.findOwn(TOURIST_ID, RESERVATION_ID)).thenReturn(reservation);
    }

    @Test
    void ratesAFinishedReservation() {
        reservation.confirm();
        reservation.finish();
        ownReservation();
        when(ratingRepository.saveAndFlush(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Rating rating = service.rate(TOURIST_ID, RESERVATION_ID, form);

        assertEquals(4, rating.getStars());
        assertEquals("Muy linda", rating.getTitle());
        assertEquals(NOW, rating.getCreatedAt());
    }

    // 1a: todavia no termino
    @Test
    void reservationThatDidNotFinishCannotBeRated() {
        reservation.confirm();
        ownReservation();

        assertThrows(IllegalStateException.class, () -> service.rate(TOURIST_ID, RESERVATION_ID, form));
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    // 4a: una sola valoracion por reserva
    @Test
    void reservationCanBeRatedOnlyOnce() {
        reservation.confirm();
        reservation.finish();
        ownReservation();
        when(ratingRepository.existsByReservation_Id(RESERVATION_ID)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.rate(TOURIST_ID, RESERVATION_ID, form));
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    // Solo se valora una actividad que hizo el turista: la reserva de otro no existe para el (404)
    @Test
    void otherTouristsReservationCannotBeRated() {
        when(reservationService.findOwn(99L, RESERVATION_ID)).thenThrow(new ReservationNotFoundException(RESERVATION_ID));

        assertThrows(ReservationNotFoundException.class, () -> service.rate(99L, RESERVATION_ID, form));
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    // CU-08 paso 6: el promedio sale de las valoraciones
    @Test
    void activityRatingsAverageAndDistribution() {
        reservation.confirm();
        reservation.finish();
        List<Rating> ratings = List.of(
                new Rating(reservation, 5, "A", "a", NOW),
                new Rating(reservation, 5, "B", "b", NOW),
                new Rating(reservation, 4, "C", "c", NOW),
                new Rating(reservation, 1, "D", "d", NOW));
        when(ratingRepository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(5L)).thenReturn(ratings);

        RatingService.ActivityRatings result = service.findForActivity(5L);

        assertEquals(4, result.count());
        assertEquals(3.75, result.average());
        assertEquals(2, result.countWith(5));
        assertEquals(50, result.percentWith(5));
        assertEquals(0, result.countWith(3));
    }

    // Lo que mas destacan: positivas, las de mas estrellas primero y, a igual puntaje, la resena mas completa
    @Test
    void highlightsArePositiveMostStarsAndMostDetailedFirst() {
        reservation.confirm();
        reservation.finish();
        Rating shortFive = new Rating(reservation, 5, "Corta", "Linda.", NOW);
        Rating longFive = new Rating(reservation, 5, "Larga", "El guía explicó la historia del faro con mucho detalle.", NOW);
        Rating four = new Rating(reservation, 4, "Cuatro", "Muy buena, aunque hizo frío.", NOW);
        Rating two = new Rating(reservation, 2, "Dos", "No me gustó la organización del recorrido, muy larga la espera.", NOW);
        Rating otherFour = new Rating(reservation, 4, "Otra", "Bien.", NOW);
        when(ratingRepository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(5L))
                .thenReturn(List.of(shortFive, two, otherFour, four, longFive));

        RatingService.ActivityRatings result = service.findForActivity(5L);

        assertEquals(List.of(longFive, shortFive, four), result.highlights());
        assertEquals(80, result.recommendedPercent());
        assertEquals("Muy bueno", result.averageLabel());
    }

    // Con pocas valoraciones no hay destacados: serian las mismas de la lista
    @Test
    void noHighlightsWithFewRatings() {
        reservation.confirm();
        reservation.finish();
        when(ratingRepository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(5L)).thenReturn(List.of(
                new Rating(reservation, 5, "A", "a", NOW), new Rating(reservation, 5, "B", "b", NOW)));

        RatingService.ActivityRatings result = service.findForActivity(5L);

        assertTrue(result.highlights().isEmpty());
        assertEquals("Excelente", result.averageLabel());
        assertEquals(100, result.recommendedPercent());
    }

    @Test
    void noRatingsMeansZeroAverage() {
        when(ratingRepository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        RatingService.ActivityRatings result = service.findForActivity(5L);

        assertTrue(result.isEmpty());
        assertEquals(0, result.average());
        assertEquals(0, result.percentWith(5));
    }
}
