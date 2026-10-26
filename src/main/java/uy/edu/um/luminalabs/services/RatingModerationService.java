package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.Rating;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.ModerationEvent;
import uy.edu.um.luminalabs.repositories.RatingRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

// Moderacion de valoraciones: el administrador oculta las que no corresponden (insultos, datos personales,
// publicidad...) y puede volver a mostrarlas. No se borran: el turista no puede volver a valorar esa reserva.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingModerationService {

    private final RatingRepository ratingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Pestanas del listado
    public enum Filter {
        ALL(rating -> true),
        VISIBLE(rating -> !rating.isHidden()),
        LOW(rating -> rating.getStars() <= 2),
        HIDDEN(Rating::isHidden);

        private final Predicate<Rating> predicate;

        Filter(Predicate<Rating> predicate) {
            this.predicate = predicate;
        }
    }

    // Las de la pestana que coinciden con la busqueda (titular, resena, actividad o autor), la mas nueva primero
    public RatingSearch search(Filter filter, String query) {
        String wanted = normalize(query);
        List<Rating> matching = ratingRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(rating -> wanted.isEmpty() || normalize(rating.getTitle() + " " + rating.getComment() + " "
                        + rating.getActivity().getName() + " " + rating.getReservation().getTourist().getFullName())
                        .contains(wanted))
                .toList();
        return new RatingSearch(matching.stream().filter(filter.predicate).toList(), count(matching, Filter.ALL),
                count(matching, Filter.VISIBLE), count(matching, Filter.LOW), count(matching, Filter.HIDDEN));
    }

    // Deja de verse en la actividad y de contar en el promedio; se le avisa al turista con el motivo
    @Transactional
    public Rating hide(Long ratingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Escribí el motivo: se lo enviamos por correo a quien la escribió.");
        }
        Rating rating = find(ratingId);
        rating.hide(reason, LocalDateTime.now(clock));
        eventPublisher.publishEvent(new ModerationEvent.RatingHidden(MailRecipient.from(rating.getReservation().getTourist()),
                rating.getActivity().getName(), rating.getTitle(), rating.getHiddenReason()));
        return rating;
    }

    @Transactional
    public Rating restore(Long ratingId) {
        Rating rating = find(ratingId);
        rating.restore();
        return rating;
    }

    private Rating find(Long ratingId) {
        return ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalStateException("La valoración no existe."));
    }

    private static long count(List<Rating> ratings, Filter filter) {
        return ratings.stream().filter(filter.predicate).count();
    }

    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    public record RatingSearch(List<Rating> ratings, long all, long visible, long low, long hidden) {
    }
}
