package uy.edu.um.luminalabs.payments;

// Con que tarjeta se cobra. Cada pasarela recibe la tarjeta de una forma distinta:
//  - CardDetails: los datos de la tarjeta tal cual (solo la pasarela simulada, que no cobra de verdad);
//  - CardToken: un token que genera Mercado Pago en el navegador del turista. El numero de tarjeta nunca pasa
//    por nuestro servidor, que es como se hace con una pasarela real (normas PCI).
public sealed interface PaymentCard permits PaymentCard.CardDetails, PaymentCard.CardToken {

    record CardDetails(String number, String holder, String expiry, String cvv) implements PaymentCard {

        // Para que el numero de tarjeta y el CVV nunca terminen en un log
        @Override
        public String toString() {
            return "CardDetails[holder=" + holder + "]";
        }
    }

    // paymentMethodId es la marca ("visa", "master"), paymentTypeId el tipo ("credit_card", "debit_card")
    record CardToken(String token, String paymentMethodId, String paymentTypeId, int installments,
                     String payerEmail) implements PaymentCard {
    }
}
