package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.entities.SlotStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findTop30ByActivityIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long activityId, SlotStatus status, LocalDateTime after);

    // "Desde $X" y proxima fecha de cada actividad, en una sola consulta para todas las tarjetas
    @Query("""
            select s.activity.id as activityId, min(s.price) as fromPrice, min(s.startTime) as nextStart
            from Slot s
            where s.activity.id in :activityIds and s.status = :status and s.startTime > :after
            group by s.activity.id""")
    List<SlotSummary> summarize(Collection<Long> activityIds, SlotStatus status, LocalDateTime after);

    interface SlotSummary {
        Long getActivityId();

        BigDecimal getFromPrice();

        LocalDateTime getNextStart();
    }
}
