package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// RF-20: estados de una reserva
@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    PENDING_PAYMENT("Pendiente de pago"),
    CONFIRMED("Confirmada"),
    CANCELLED("Cancelada"),
    EXPIRED("Vencida"),
    FINISHED("Terminada");

    private final String label;
}
