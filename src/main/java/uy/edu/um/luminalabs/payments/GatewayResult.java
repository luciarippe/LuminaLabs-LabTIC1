package uy.edu.um.luminalabs.payments;

// Respuesta de la pasarela, ya traducida a lo que le importa a la aplicacion
public record GatewayResult(Outcome outcome, String externalId) {

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

    public static GatewayResult of(Outcome outcome) {
        return new GatewayResult(outcome, null);
    }
}
