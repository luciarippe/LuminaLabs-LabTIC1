package uy.edu.um.luminalabs.events;

// RF-26 y CU-18 paso 2: una reserva vencio sin pago
public record ReservationExpiredEvent(MailRecipient recipient, String reservationCode, Long activityId,
                                      String activityName) {
}
