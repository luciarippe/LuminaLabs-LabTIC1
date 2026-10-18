package uy.edu.um.luminalabs.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // CU-13: reservas del turista con lo que muestra el listado, en una sola consulta
    @EntityGraph(attributePaths = {"slot", "slot.activity", "slot.activity.business"})
    List<Reservation> findByTourist_IdOrderBySlot_StartTimeAsc(Long touristId);

    // CU-13 paso 5: detalle completo de una reserva
    @EntityGraph(attributePaths = {"tourist", "slot", "slot.activity", "slot.activity.business",
            "slot.activity.location", "participants"})
    Optional<Reservation> findWithDetailsById(Long id);

    // CU-16: bloquea la fila de la reserva mientras se decide si se puede cobrar. Si el turista aprieta
    // "Pagar" dos veces (o en dos pestanas), el segundo pedido espera y ve el intento que registro el primero.
    // La consulta trae solo la reserva para no bloquear tambien el slot (eso frenaria otras reservas).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(Long id);

    // CU-18 paso 1: ids de las reservas pendientes de pago creadas antes de cierto momento
    @Query("select r.id from Reservation r where r.status = :status and r.createdAt < :before")
    List<Long> findIdsByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime before);

    // CU-18 paso 3: reservas confirmadas cuyo slot ya empezo (el servicio revisa si ademas ya termino)
    @EntityGraph(attributePaths = {"slot", "slot.activity"})
    List<Reservation> findByStatusAndSlot_StartTimeBefore(ReservationStatus status, LocalDateTime before);

    // CU-04 5a y 5b: reservas que todavia ocupan lugares en un slot
    @EntityGraph(attributePaths = "tourist")
    List<Reservation> findBySlot_IdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

    // CU-11 pasos 3 y 8: reservas vigentes (slot que no empezo) de las actividades de un emprendimiento
    @EntityGraph(attributePaths = {"tourist", "slot"})
    List<Reservation> findBySlot_Activity_Business_IdAndStatusInAndSlot_StartTimeAfter(
            Long businessId, Collection<ReservationStatus> statuses, LocalDateTime after);
}
