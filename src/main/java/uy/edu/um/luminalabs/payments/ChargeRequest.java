package uy.edu.um.luminalabs.payments;

import java.math.BigDecimal;

// Lo que se le manda a la pasarela para cobrar. "reference" identifica el intento (ver Payment.getReference):
// si la pasarela recibe dos veces la misma referencia, no cobra dos veces.
public record ChargeRequest(String reference, BigDecimal amount, String currency,
                            String cardNumber, String cardHolder, String expiry, String cvv) {

    // Para que el numero de tarjeta y el CVV nunca terminen en un log
    @Override
    public String toString() {
        return "ChargeRequest[reference=" + reference + ", amount=" + amount + " " + currency + "]";
    }
}
