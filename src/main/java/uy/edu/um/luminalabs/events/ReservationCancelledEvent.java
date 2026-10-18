package uy.edu.um.luminalabs.events;

import uy.edu.um.luminalabs.entities.CancellationReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// RF-26 y CU-05 paso 9: se cancelo una reserva. Se avisa al turista y a los prestadores del emprendimiento.
public record ReservationCancelledEvent(MailRecipient tourist, List<MailRecipient> providers, Long reservationId,
                                        String reservationCode, String activityName, LocalDateTime startTime,
                                        int participantCount, CancellationReason reason, BigDecimal refundAmount) {
}
