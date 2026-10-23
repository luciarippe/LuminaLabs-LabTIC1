package uy.edu.um.luminalabs.dto;

import uy.edu.um.luminalabs.payments.PaymentCard;

// Lo que PaymentService necesita de la pantalla de pago, venga del formulario de la pasarela simulada (CardForm)
// o del de Mercado Pago (CardTokenForm): con que tarjeta cobrar y que guardar de ella (ver Payment).
public interface PaymentCardForm {

    String getCardHolder();

    String getLastDigits();

    PaymentCard toPaymentCard();
}
