package uy.edu.um.luminalabs.events;

// Se registro una devolucion pendiente: hay que pedirsela a la pasarela despues de confirmar la transaccion
// (ver RefundService.onRefundRequested)
public record RefundRequestedEvent(Long refundPaymentId) {
}
