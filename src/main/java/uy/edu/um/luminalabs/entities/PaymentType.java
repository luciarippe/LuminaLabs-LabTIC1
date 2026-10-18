package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Un pago puede ser el cobro de la reserva o la devolucion de una cancelacion (RF-19, CU-05)
@Getter
@RequiredArgsConstructor
public enum PaymentType {
    CHARGE("Cobro"),
    REFUND("Devolución");

    private final String label;
}
