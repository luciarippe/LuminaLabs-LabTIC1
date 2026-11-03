package uy.edu.um.luminalabs.events;

import java.time.LocalDateTime;

// Alguien pidio recuperar la contrasena de una cuenta: se le manda el enlace. El token va solo en este correo.
public record PasswordResetRequestedEvent(MailRecipient recipient, String username, String token,
                                          LocalDateTime expiresAt) {
}
