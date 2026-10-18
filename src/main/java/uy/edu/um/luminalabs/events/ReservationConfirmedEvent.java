package uy.edu.um.luminalabs.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// CU-01 paso 10 y CU-16 paso 7: el pago fue aprobado y la reserva quedo confirmada
public record ReservationConfirmedEvent(MailRecipient recipient, Long reservationId, String reservationCode,
                                        String activityName, LocalDateTime startTime, String address,
                                        BigDecimal amount) {
}
