package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.ModerationEvent;
import uy.edu.um.luminalabs.repositories.RatingRepository;

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

// Moderacion de valoraciones: ocultar y volver a mostrar
@ExtendWith(MockitoExtension.class)
class RatingModerationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RatingModerationService service;
    private Activity activity;
    private Tourist tourist;

    @BeforeEach
    void setUp() {
        service = new RatingModerationService(ratingRepository, eventPublisher, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        activity = new Activity();
        activity.setName("Kayak al atardecer");
        tourist = Tourist.builder().firstName("Ana").lastName("Pérez").email("ana@uy").birthDate(LocalDate.of(1990, 1, 1)).build();
    }

    private Rating rating(int stars, String title) {
        Slot slot = new Slot(activity, NOW.minusDays(3), new BigDecimal("1500"), 10);
        Reservation reservation = new Reservation(tourist, slot, List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1))),
                NOW.minusDays(5));
        reservation.confirm();
        reservation.finish();
        return new Rating(reservation, stars, title, "Comentario de " + title, NOW.minusDays(1));
    }

    @Test
    void hidingTakesItOutAndNotifiesTheTourist() {
        Rating rating = rating(1, "Insultos");
        when(ratingRepository.findById(7L)).thenReturn(Optional.of(rating));

        service.hide(7L, " Lenguaje ofensivo ");

        assertTrue(rating.isHidden());
        assertEquals("Lenguaje ofensivo", rating.getHiddenReason());
        assertEquals(NOW, rating.getHiddenAt());
        verify(eventPublisher).publishEvent(any(ModerationEvent.RatingHidden.class));
    }

    @Test
    void hidingRequiresAReasonAndCanBeUndone() {
        assertThrows(IllegalArgumentException.class, () -> service.hide(7L, ""));

        Rating rating = rating(2, "Regular");
        rating.hide("Motivo", NOW);
        when(ratingRepository.findById(7L)).thenReturn(Optional.of(rating));
        service.restore(7L);

        assertFalse(rating.isHidden());
        assertNull(rating.getHiddenReason());
    }

    @Test
    void searchFiltersByTabAndText() {
        Rating great = rating(5, "Excelente");
        Rating bad = rating(1, "Pésimo");
        Rating hidden = rating(4, "Publicidad");
        hidden.hide("Spam", NOW);
        when(ratingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(great, bad, hidden));

        assertEquals(List.of(bad), service.search(RatingModerationService.Filter.LOW, "").ratings());
        assertEquals(List.of(hidden), service.search(RatingModerationService.Filter.HIDDEN, "").ratings());
        RatingModerationService.RatingSearch search = service.search(RatingModerationService.Filter.ALL, "pesimo");
        assertEquals(List.of(bad), search.ratings());
        assertEquals(1, search.low());
    }

    // Una valoracion oculta no se muestra en la actividad ni cuenta en el promedio (la consulta publica las excluye)
    @Test
    void hiddenRatingsAreNotPublic() {
        RatingRepository repository = mock(RatingRepository.class);
        RatingService ratingService = new RatingService(repository, mock(ReservationService.class), Clock.systemDefaultZone());
        when(repository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of(rating(5, "Visible")));

        assertEquals(1, ratingService.findForActivity(1L).count());
        verify(repository, never()).findAllByOrderByCreatedAtDesc();
    }
}
