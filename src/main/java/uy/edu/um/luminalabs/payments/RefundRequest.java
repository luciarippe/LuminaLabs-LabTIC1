package uy.edu.um.luminalabs.payments;

import java.math.BigDecimal;

// Devolucion de parte o todo de un cobro anterior (RF-19). "reference" identifica la devolucion: si la pasarela
// la recibe dos veces (ej. un reintento despues de un timeout), no devuelve dos veces.
public record RefundRequest(String reference, String chargeExternalId, BigDecimal amount, String currency) {
}
