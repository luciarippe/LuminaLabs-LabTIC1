package uy.edu.um.luminalabs.services;

import java.math.BigDecimal;

// CU-05 pasos 2 a 5: cuanto se paga, cuanto se cobra de cargo y cuanto se devuelve si se cancela ahora.
// Se calcula igual para mostrarlo antes de confirmar y para la cancelacion en si.
public record CancellationQuote(Case policyCase, BigDecimal paid, BigDecimal fee, BigDecimal refund) {

    public enum Case {
        // 2a: pendiente de pago, no hay nada que devolver
        NOT_PAID,
        // 4b: el prestador cambio el horario, devolucion total
        SCHEDULE_CHANGED,
        // Dentro del plazo: se devuelve lo pagado menos el cargo
        WITHIN_DEADLINE,
        // 4a: fuera del plazo, no hay devolucion
        OUTSIDE_DEADLINE,
        // El prestador cancelo el slot o el emprendimiento fue suspendido: devolucion total
        CANCELLED_BY_PLATFORM
    }

    public boolean hasRefund() {
        return refund.signum() > 0;
    }
}
