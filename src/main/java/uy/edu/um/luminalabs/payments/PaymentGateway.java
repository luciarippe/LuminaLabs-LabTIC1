package uy.edu.um.luminalabs.payments;

// Servicio externo de pagos (RF-17). La aplicacion depende de esta interfaz y no de una pasarela concreta:
// hoy la implementa HttpPaymentGateway (que llama a la pasarela simulada por HTTP); para usar una real
// alcanza con otra implementacion. En los tests se reemplaza por un mock.
public interface PaymentGateway {

    // Pide el cobro. Nunca lanza excepciones por fallas de la pasarela: las devuelve como resultado.
    GatewayResult charge(ChargeRequest request);

    // Pregunta que paso con un pedido anterior (RF-18: ante un intento sin respuesta, antes de cobrar de nuevo)
    GatewayResult findByReference(String reference);

    // Devuelve dinero de un cobro aprobado (RF-19). Igual que charge, devuelve las fallas como resultado.
    GatewayResult refund(RefundRequest request);
}
