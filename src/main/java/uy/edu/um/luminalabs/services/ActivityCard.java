package uy.edu.um.luminalabs.services;

import uy.edu.um.luminalabs.entities.Activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Lo que muestra una tarjeta del catalogo. fromPrice y nextStart son null si no hay fechas disponibles.
public record ActivityCard(Activity activity, BigDecimal fromPrice, LocalDateTime nextStart) {

    public boolean hasUpcomingDates() {
        return nextStart != null;
    }
}
