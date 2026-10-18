package uy.edu.um.luminalabs.exceptions;

// CU-16 4a, 4b y 5a: no se puede intentar el pago ahora. El mensaje es para el turista.
public class PaymentNotPossibleException extends RuntimeException {

    public PaymentNotPossibleException(String message) {
        super(message);
    }
}
