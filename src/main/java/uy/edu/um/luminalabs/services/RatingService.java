package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.RatingForm;
import uy.edu.um.luminalabs.entities.Rating;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;
import uy.edu.um.luminalabs.repositories.RatingRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// CU-08: el turista valora una reserva terminada; las valoraciones se publican en el detalle de la actividad.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ReservationService reservationService;
    private final Clock clock;

    // Pasos 1 y 2: la reserva del turista, verificando que se pueda valorar (si no, el mensaje de por que)
    public Reservation findRateable(Long touristId, Long reservationId) {
        Reservation reservation = reservationService.findOwn(touristId, reservationId);
        if (reservation.getStatus() != ReservationStatus.FINISHED) {
            throw new IllegalStateException("Vas a poder valorar esta experiencia cuando haya terminado.");
        }
        // 4a
        if (ratingRepository.existsByReservation_Id(reservationId)) {
            throw new IllegalStateException("Ya valoraste esta experiencia. ¡Gracias por contarnos!");
        }
        return reservation;
    }

    // Pasos 3 a 6. El promedio no se guarda: se calcula con las valoraciones cada vez (ActivityRatings).
    // Si el formulario se envia dos veces a la vez, la restriccion unica de la base rechaza la segunda
    // (DataIntegrityViolationException, la atrapa el controlador).
    @Transactional
    public Rating rate(Long touristId, Long reservationId, RatingForm form) {
        Reservation reservation = findRateable(touristId, reservationId);
        Rating rating = new Rating(reservation, form.getStars(), form.getTitle(), form.getComment(),
                LocalDateTime.now(clock));
        return ratingRepository.saveAndFlush(rating);
    }

    public Optional<Rating> findByReservation(Long reservationId) {
        return ratingRepository.findByReservation_Id(reservationId);
    }

    // CU-07 paso 7: valoraciones de la actividad con su promedio
    public ActivityRatings findForActivity(Long activityId) {
        return new ActivityRatings(ratingRepository.findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(activityId));
    }

    // Ids de las reservas (de la lista) que ya tienen valoracion
    public Set<Long> findRatedReservationIds(Collection<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(ratingRepository.findRatedReservationIds(
                reservations.stream().map(Reservation::getId).toList()));
    }

    // Las valoraciones de una actividad (la mas nueva primero) con su promedio y cuantas hay de cada puntaje
    public record ActivityRatings(List<Rating> ratings) {

        // "Lo que mas destacan": cuantas se muestran y desde cuantas valoraciones (con menos se repetirian con la lista)
        public static final int HIGHLIGHTS_SHOWN = 3;
        public static final int MIN_RATINGS_FOR_HIGHLIGHTS = 3;

        // Mas relevantes: las de 4 o 5 estrellas (si las hay), primero las de mas estrellas, despues las que
        // cuentan mas (resena mas larga) y por ultimo las mas nuevas
        public List<Rating> highlights() {
            if (ratings.size() < MIN_RATINGS_FOR_HIGHLIGHTS) {
                return List.of();
            }
            List<Rating> positive = ratings.stream().filter(rating -> rating.getStars() >= 4).toList();
            return (positive.isEmpty() ? ratings : positive).stream()
                    .sorted(Comparator.comparingInt(Rating::getStars).reversed()
                            .thenComparing(Comparator.comparingInt((Rating rating) -> rating.getComment().length()).reversed())
                            .thenComparing(Comparator.comparing(Rating::getCreatedAt).reversed()))
                    .limit(HIGHLIGHTS_SHOWN)
                    .toList();
        }

        // Palabra que acompana al promedio (como "Excelente 4,8")
        public String averageLabel() {
            double average = average();
            if (average >= 4.5) {
                return "Excelente";
            }
            if (average >= 4) {
                return "Muy bueno";
            }
            if (average >= 3) {
                return "Bueno";
            }
            return average >= 2 ? "Regular" : "Malo";
        }

        // Porcentaje de turistas que la recomiendan: los que le dieron 4 o 5 estrellas
        public int recommendedPercent() {
            return ratings.isEmpty() ? 0 : (int) Math.round(
                    ratings.stream().filter(rating -> rating.getStars() >= 4).count() * 100.0 / ratings.size());
        }

        public boolean isEmpty() {
            return ratings.isEmpty();
        }

        public int count() {
            return ratings.size();
        }

        // 0 si no hay valoraciones
        public double average() {
            return ratings.stream().mapToInt(Rating::getStars).average().orElse(0);
        }

        public long countWith(int stars) {
            return ratings.stream().filter(rating -> rating.getStars() == stars).count();
        }

        // Para las barras del resumen
        public int percentWith(int stars) {
            return ratings.isEmpty() ? 0 : (int) Math.round(countWith(stars) * 100.0 / ratings.size());
        }
    }
}
