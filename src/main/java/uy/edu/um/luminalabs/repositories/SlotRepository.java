package uy.edu.um.luminalabs.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.entities.SlotStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    // CU-04: todos los slots de una actividad (tambien los pasados y cancelados) para el espacio del prestador
    List<Slot> findByActivityIdOrderByStartTimeAsc(Long activityId);

    @EntityGraph(attributePaths = {"activity", "activity.business", "activity.business.providers"})
    Optional<Slot> findWithActivityById(Long id);

    // CU-01 paso 6 (RNF-12): bloqueo pesimista. "SELECT ... FOR UPDATE" bloquea la fila del slot hasta que
    // termina la transaccion: si dos turistas reservan a la vez, el segundo espera a que el primero termine
    // y despues lee la disponibilidad ya descontada. Asi nunca se venden mas lugares que la capacidad.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s join fetch s.activity a join fetch a.business where s.id = :id")
    Optional<Slot> findByIdForUpdate(Long id);

    // Detalle de la actividad: los proximos horarios para el calendario (alcanza para varios meses)
    List<Slot> findTop100ByActivityIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long activityId, SlotStatus status, LocalDateTime after);

    // "Desde $X", proxima fecha y ultima fecha de cada actividad, en una sola consulta para todas las tarjetas.
    // La ultima fecha la usa el aviso al prestador de horarios por terminarse.
    @Query("""
            select s.activity.id as activityId, min(s.price) as fromPrice, min(s.startTime) as nextStart,
                   max(s.startTime) as lastStart
            from Slot s
            where s.activity.id in :activityIds and s.status = :status and s.startTime > :after
            group by s.activity.id""")
    List<SlotSummary> summarize(Collection<Long> activityIds, SlotStatus status, LocalDateTime after);

    // Espacio del prestador: proximos horarios activos de todas sus actividades, el mas cercano primero
    @Query("""
            select s from Slot s join fetch s.activity a join a.business b join b.providers p
            where p.id = :providerId and s.status = :status and s.startTime > :after
            order by s.startTime asc""")
    List<Slot> findUpcomingForProvider(Long providerId, SlotStatus status, LocalDateTime after, Pageable pageable);

    // Cantidad de proximos horarios activos y lugares ya reservados en ellos
    @Query("""
            select count(s) as slots, coalesce(sum(s.maxCapacity - s.availability), 0) as reservedSeats
            from Slot s join s.activity a join a.business b join b.providers p
            where p.id = :providerId and s.status = :status and s.startTime > :after""")
    UpcomingTotals countUpcomingForProvider(Long providerId, SlotStatus status, LocalDateTime after);

    interface UpcomingTotals {
        long getSlots();

        long getReservedSeats();
    }

    interface SlotSummary {
        Long getActivityId();

        BigDecimal getFromPrice();

        LocalDateTime getNextStart();

        LocalDateTime getLastStart();
    }
}
