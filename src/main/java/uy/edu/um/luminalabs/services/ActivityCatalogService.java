package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.ActivitySearchCriteria;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.entities.SlotStatus;
import uy.edu.um.luminalabs.exceptions.ActivityNotFoundException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.ActivityRepository.DepartmentCount;
import uy.edu.um.luminalabs.repositories.SlotRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository.SlotSummary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static uy.edu.um.luminalabs.repositories.ActivitySpecifications.*;

// Portal publico: explorar, buscar y ver el detalle de actividades (CU-07). No requiere sesion.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityCatalogService {

    private final ActivityRepository activityRepository;
    private final SlotRepository slotRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final Clock clock;

    public List<ActivityCard> search(ActivitySearchCriteria criteria) {
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<Activity> specification = publiclyVisible();
        if (criteria.hasKeyword()) {
            specification = specification.and(matchesKeyword(criteria.getQ()));
        }
        if (criteria.getDepartment() != null) {
            specification = specification.and(inDepartment(criteria.getDepartment()));
        }
        if (criteria.getLocationId() != null) {
            specification = specification.and(inLocation(criteria.getLocationId()));
        }
        if (criteria.getCategoryId() != null) {
            specification = specification.and(inCategory(criteria.getCategoryId()));
        }
        if (criteria.hasSlotFilters()) {
            specification = specification.and(hasSlotMatching(now,
                    criteria.getDateFrom() == null ? null : criteria.getDateFrom().atStartOfDay(),
                    criteria.getDateTo() == null ? null : criteria.getDateTo().plusDays(1).atStartOfDay(),
                    criteria.getMinPrice(), criteria.getMaxPrice()));
        }

        List<Activity> activities = activityRepository.findAll(specification);
        return toCards(activities, now).stream()
                .sorted(comparatorFor(criteria.getSort()))
                .toList();
    }

    public Activity findPublicActivity(Long id) {
        return activityRepository.findWithDetailsByIdAndBusinessStatus(id, BusinessStatus.APPROVED)
                .orElseThrow(() -> new ActivityNotFoundException(id));
    }

    // CU-07 paso 7: proximos slots activos con su precio y disponibilidad
    public List<Slot> findUpcomingSlots(Long activityId) {
        return slotRepository.findTop30ByActivityIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                activityId, SlotStatus.ACTIVE, LocalDateTime.now(clock));
    }

    public List<ActivityCategory> findCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public List<DepartmentCount> findDestinations() {
        return activityRepository.countByDepartment(BusinessStatus.APPROVED);
    }

    private List<ActivityCard> toCards(List<Activity> activities, LocalDateTime now) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        Map<Long, SlotSummary> summaries = slotRepository.summarize(ids, SlotStatus.ACTIVE, now).stream()
                .collect(Collectors.toMap(SlotSummary::getActivityId, Function.identity()));
        return activities.stream().map(activity -> {
            SlotSummary summary = summaries.get(activity.getId());
            return summary == null
                    ? new ActivityCard(activity, null, null)
                    : new ActivityCard(activity, summary.getFromPrice(), summary.getNextStart());
        }).toList();
    }

    // Las actividades sin fechas disponibles van siempre al final
    static Comparator<ActivityCard> comparatorFor(ActivitySearchCriteria.Sort sort) {
        Comparator<ActivityCard> withDatesFirst = Comparator.comparing(card -> !card.hasUpcomingDates());
        Comparator<ActivityCard> byName = Comparator.comparing(card -> card.activity().getName());
        return switch (sort) {
            case PRICE -> withDatesFirst.thenComparing(ActivityCard::fromPrice,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byName);
            case DATE -> withDatesFirst.thenComparing(ActivityCard::nextStart,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byName);
            // Cuando existan las valoraciones (RF-23), "recomendadas" deberia ordenar por puntaje
            case RECOMMENDED -> withDatesFirst.thenComparing(byName);
        };
    }
}
