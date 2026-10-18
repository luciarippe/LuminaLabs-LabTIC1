package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Quien o que cancelo la reserva. Solo la cancelacion del turista puede tener cargo; en las otras se
// devuelve todo (CU-04 5b, RF-06).
@Getter
@RequiredArgsConstructor
public enum CancellationReason {
    TOURIST("Cancelada por el turista"),
    SLOT_CANCELLED("El prestador canceló el horario"),
    BUSINESS_SUSPENDED("El emprendimiento fue suspendido");

    private final String label;
}
