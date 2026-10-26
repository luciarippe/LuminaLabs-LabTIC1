package uy.edu.um.luminalabs.events;

// Avisos por correo de lo que hace un administrador sobre una cuenta o una valoracion
public sealed interface ModerationEvent {

    record AccountBlocked(MailRecipient recipient, String reason) implements ModerationEvent {
    }

    record AccountUnblocked(MailRecipient recipient) implements ModerationEvent {
    }

    record RatingHidden(MailRecipient recipient, String activityName, String ratingTitle, String reason)
            implements ModerationEvent {
    }
}
