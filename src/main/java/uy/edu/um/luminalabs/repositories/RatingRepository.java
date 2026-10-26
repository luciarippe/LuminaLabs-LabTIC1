package uy.edu.um.luminalabs.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Rating;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    // Detalle de la actividad: sus valoraciones visibles (las ocultas por moderacion no), la mas nueva primero,
    // con el turista (para la firma) y el slot
    @EntityGraph(attributePaths = {"reservation", "reservation.tourist", "reservation.slot"})
    List<Rating> findByActivity_IdAndHiddenAtIsNullOrderByCreatedAtDesc(Long activityId);

    Optional<Rating> findByReservation_Id(Long reservationId);

    boolean existsByReservation_Id(Long reservationId);

    // Mis reservas: cuales de estas reservas ya tienen valoracion (aunque este oculta: no se puede volver a valorar)
    @Query("select r.reservation.id from Rating r where r.reservation.id in :reservationIds")
    List<Long> findRatedReservationIds(Collection<Long> reservationIds);

    // Moderacion: todas, la mas nueva primero, con lo que muestra el listado
    @EntityGraph(attributePaths = {"activity", "reservation", "reservation.tourist", "reservation.slot"})
    List<Rating> findAllByOrderByCreatedAtDesc();

    // Tarjetas del catalogo: promedio y cantidad de valoraciones visibles de cada actividad, en una sola consulta
    // (las actividades sin valoraciones no aparecen)
    @Query("""
            select r.activity.id as activityId, avg(r.stars) as average, count(r) as total
            from Rating r
            where r.activity.id in :activityIds and r.hiddenAt is null
            group by r.activity.id""")
    List<RatingSummary> summarize(Collection<Long> activityIds);

    // API publica (CU-17 c): las actividades de un departamento con mejor promedio, considerando las valoraciones
    // visibles de reservas cuyo horario fue en el periodo [from, to). Empates: mas valoraciones primero.
    @Query("""
            select r.activity.id as activityId, avg(r.stars) as average, count(r) as total
            from Rating r join r.reservation res join res.slot s join r.activity a join a.business b
            where a.location.department = :department and b.status = :businessStatus and r.hiddenAt is null
              and s.startTime >= :from and s.startTime < :to
            group by r.activity.id
            order by avg(r.stars) desc, count(r) desc, r.activity.id asc""")
    List<RatingSummary> findTopInDepartment(Department department, BusinessStatus businessStatus,
                                            LocalDateTime from, LocalDateTime to, Pageable pageable);

    interface RatingSummary {
        Long getActivityId();

        Double getAverage();

        long getTotal();
    }

    // Gestion de usuarios: las valoraciones de un turista
    @EntityGraph(attributePaths = {"activity"})
    List<Rating> findByReservation_Tourist_IdOrderByCreatedAtDesc(Long touristId);
}
