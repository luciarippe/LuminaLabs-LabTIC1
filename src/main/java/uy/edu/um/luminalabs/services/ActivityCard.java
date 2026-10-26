package uy.edu.um.luminalabs.services;

import uy.edu.um.luminalabs.entities.Activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Lo que muestra una tarjeta del catalogo. fromPrice y nextStart son null si no hay fechas disponibles;
// averageRating es null si no tiene valoraciones (visibles).
public record ActivityCard(Activity activity, BigDecimal fromPrice, LocalDateTime nextStart,
                           Double averageRating, long ratingCount) {

    // Orden "Recomendadas": cada actividad arranca como si tuviera PRIOR_COUNT valoraciones de PRIOR_STARS, que
    // pesan menos a medida que llegan valoraciones reales. Asi una sola de 5 estrellas no supera a muchas buenas:
    // 1 de 5 -> 3,7; 8 con promedio 4,5 -> 4,2; ninguna -> 3,0.
    static final double PRIOR_STARS = 3.0;
    static final int PRIOR_COUNT = 2;

    // Sin valoraciones
    public ActivityCard(Activity activity, BigDecimal fromPrice, LocalDateTime nextStart) {
        this(activity, fromPrice, nextStart, null, 0);
    }

    public boolean hasUpcomingDates() {
        return nextStart != null;
    }

    public boolean hasRatings() {
        return ratingCount > 0;
    }

    public double recommendationScore() {
        double total = hasRatings() ? averageRating * ratingCount : 0;
        return (PRIOR_STARS * PRIOR_COUNT + total) / (PRIOR_COUNT + ratingCount);
    }
}
