package uy.edu.um.luminalabs.events;

import java.time.LocalDateTime;

// RF-26 y CU-04 5a: el prestador cambio el horario de un slot con reservas. Un evento por reserva.
public record SlotRescheduledEvent(MailRecipient recipient, Long reservationId, String reservationCode,
                                   String activityName, LocalDateTime previousStart, LocalDateTime newStart) {
}
