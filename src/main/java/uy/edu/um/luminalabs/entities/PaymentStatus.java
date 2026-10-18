package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// RF-18: estado de cada intento de pago
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    // Estado inicial: el intento se guarda ANTES de llamar a la pasarela. Si la respuesta nunca llega
    // (timeout, la app se cae), queda asi y hay que preguntarle a la pasarela antes de volver a cobrar.
    NO_RESPONSE("Sin respuesta"),
    APPROVED("Aprobado"),
    REJECTED("Rechazado"),
    // Solo devoluciones (RF-19): todavia no se hizo o fallo; el proceso automatico la reintenta (CU-18 paso 4).
    // Reintentar una devolucion es seguro: va siempre con la misma referencia y la pasarela no devuelve dos veces.
    PENDING("Pendiente");

    private final String label;
}
