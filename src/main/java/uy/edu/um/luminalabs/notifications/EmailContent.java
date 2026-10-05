package uy.edu.um.luminalabs.notifications;

import java.util.List;

// Contenido de un correo; se renderiza con la plantilla templates/mail/notification.html.
// highlightLabel/highlight (ej. "Motivo") y buttonLabel/buttonUrl son opcionales (null = no se muestran).
public record EmailContent(String subject, String title, List<String> paragraphs,
                           String highlightLabel, String highlight,
                           String buttonLabel, String buttonUrl) {
}
