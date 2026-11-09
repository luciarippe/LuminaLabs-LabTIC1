package uy.edu.um.luminalabs.payments;

import java.math.BigDecimal;

// Respuesta de la pasarela, ya traducida a lo que le importa a la aplicacion.
// "amount": el movimiento de dinero tal como lo informa la pasarela, con signo. En una devolucion es negativo
// (ej. -1800.00: se le devuelven $ 1.800 al turista). null si la pasarela no lo informa.
public record GatewayResult(Outcome outcome, String externalId, BigDecimal amount) {

    public enum Outcome {
        // Respuestas definitivas
        APPROVED,
        DECLINED,
        // La pasarela fallo o no se pudo conectar: el pedido no se proceso (RF-18 9a: el pago queda rechazado)
        UNAVAILABLE,
        // Timeout: el pedido salio pero no sabemos si se cobro (9b: queda "sin respuesta")
        NO_RESPONSE,
        // Al consultar un pedido anterior: la pasarela nunca lo recibio, asi que no se cobro
        NOT_FOUND
    }

    public GatewayResult(Outcome outcome, String externalId) {
        this(outcome, externalId, null);
    }

    public static GatewayResult of(Outcome outcome) {
        return new GatewayResult(outcome, null);
    }
}
