package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import uy.edu.um.luminalabs.payments.PaymentCard;
import uy.edu.um.luminalabs.payments.PaymentCard.CardToken;

// CU-01 paso 7 y CU-16 paso 3 con Mercado Pago. El turista completa la tarjeta en el formulario de Mercado Pago
// (Card Payment Brick, ver pay.html), que la cambia por un token en el navegador. Aca llegan solo el token y
// datos que no son secretos. Son campos ocultos que llena el Brick, asi que si alguno falta es un error del
// formulario y no algo que el turista pueda corregir: pay.html muestra un mensaje general.
@Getter
@Setter
public class CardTokenForm implements PaymentCardForm {

    @NotBlank
    @Size(max = 255)
    private String token;

    // Marca de la tarjeta ("visa", "master", ...)
    @NotBlank
    @Size(max = 50)
    private String paymentMethodId;

    // "credit_card" o "debit_card"
    @NotBlank
    @Size(max = 50)
    private String paymentTypeId;

    @Min(1)
    @Max(36)
    private int installments = 1;

    @NotBlank
    @Size(max = 100)
    private String cardHolder;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$")
    private String lastDigits;

    @NotBlank
    @Email
    @Size(max = 255)
    private String payerEmail;

    @Override
    public PaymentCard toPaymentCard() {
        return new CardToken(token, paymentMethodId, paymentTypeId, installments, payerEmail.trim());
    }
}
