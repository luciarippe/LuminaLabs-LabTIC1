package uy.edu.um.luminalabs.repositories;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import uy.edu.um.luminalabs.entities.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Filtros del catalogo (RF-13). Las colecciones se filtran con subconsultas "exists"
// para que una actividad no aparezca repetida si coincide por varias categorias o fechas.
public final class ActivitySpecifications {

    private ActivitySpecifications() {
    }

    // RF-12: solo actividades de emprendimientos aprobados
    public static Specification<Activity> publiclyVisible() {
        return (root, query, cb) -> cb.equal(root.get("business").get("status"), BusinessStatus.APPROVED);
    }

    public static Specification<Activity> matchesKeyword(String keyword) {
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            Subquery<Long> categoryMatch = query.subquery(Long.class);
            Root<Activity> sub = categoryMatch.from(Activity.class);
            Join<Activity, ActivityCategory> category = sub.join("categories");
            categoryMatch.select(sub.get("id")).where(
                    cb.equal(sub.get("id"), root.get("id")),
                    cb.like(cb.lower(category.get("name")), pattern));
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("location").get("name")), pattern),
                    cb.exists(categoryMatch));
        };
    }

    public static Specification<Activity> inDepartment(Department department) {
        return (root, query, cb) -> cb.equal(root.get("location").get("department"), department);
    }

    public static Specification<Activity> inLocation(Long locationId) {
        return (root, query, cb) -> cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<Activity> inCategory(Long categoryId) {
        return (root, query, cb) -> {
            Subquery<Long> match = query.subquery(Long.class);
            Root<Activity> sub = match.from(Activity.class);
            Join<Activity, ActivityCategory> category = sub.join("categories");
            match.select(sub.get("id")).where(
                    cb.equal(sub.get("id"), root.get("id")),
                    cb.equal(category.get("id"), categoryId));
            return cb.exists(match);
        };
    }

    // Tiene al menos un slot activo futuro dentro del rango de fechas y de precio pedido (null = sin limite)
    public static Specification<Activity> hasSlotMatching(LocalDateTime after, LocalDateTime from, LocalDateTime to,
                                                          BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            Subquery<Long> match = query.subquery(Long.class);
            Root<Slot> slot = match.from(Slot.class);
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(slot.get("activity"), root));
            conditions.add(cb.equal(slot.get("status"), SlotStatus.ACTIVE));
            conditions.add(cb.greaterThan(slot.get("startTime"), after));
            if (from != null) {
                conditions.add(cb.greaterThanOrEqualTo(slot.get("startTime"), from));
            }
            if (to != null) {
                conditions.add(cb.lessThan(slot.get("startTime"), to));
            }
            if (minPrice != null) {
                conditions.add(cb.greaterThanOrEqualTo(slot.get("price"), minPrice));
            }
            if (maxPrice != null) {
                conditions.add(cb.lessThanOrEqualTo(slot.get("price"), maxPrice));
            }
            match.select(slot.get("id")).where(conditions.toArray(Predicate[]::new));
            return cb.exists(match);
        };
    }
}
