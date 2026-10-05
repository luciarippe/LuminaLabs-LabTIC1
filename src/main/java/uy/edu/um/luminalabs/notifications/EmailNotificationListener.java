package uy.edu.um.luminalabs.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.TouristRegisteredEvent;

import java.util.List;

// Notificaciones por correo (RF-26). @TransactionalEventListener: el correo se envia solo si la
// transaccion se confirmo, asi nunca se avisa de un cambio que termino deshaciendose.
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener
    public void onTouristRegistered(TouristRegisteredEvent event) {
        MailRecipient recipient = event.recipient();
        emailSender.send(recipient, new EmailContent(
                "Tu cuenta en Uruguay Mnatural está lista",
                "¡Te damos la bienvenida!",
                List.of("Hola " + recipient.firstName() + ", tu cuenta fue creada con el usuario "
                                + event.username() + ".",
                        "Ya podés iniciar sesión para explorar experiencias en todo el país y reservar tus actividades."),
                null, null,
                "Iniciar sesión", emailSender.url("/login")));
    }

    @Async
    @TransactionalEventListener
    public void onBusinessRegistered(BusinessRegisteredEvent event) {
        MailRecipient recipient = event.recipient();
        emailSender.send(recipient, new EmailContent(
                "Recibimos la solicitud de " + event.businessName(),
                "Recibimos tu solicitud",
                List.of("Hola " + recipient.firstName() + ", recibimos la solicitud de registro de "
                                + event.businessName() + ".",
                        "Nuestro equipo va a revisar los datos y te vamos a avisar por este medio cuando tengamos "
                                + "una respuesta. Hasta que sea aprobada no vas a poder iniciar sesión."),
                null, null, null, null));
    }

    @Async
    @TransactionalEventListener
    public void onBusinessStatusChanged(BusinessStatusChangedEvent event) {
        for (MailRecipient recipient : event.recipients()) {
            emailSender.send(recipient, statusChangeContent(event, recipient));
        }
    }

    private EmailContent statusChangeContent(BusinessStatusChangedEvent event, MailRecipient recipient) {
        String business = event.businessName();
        String greeting = "Hola " + recipient.firstName() + ", ";
        return switch (event.change()) {
            case APPROVED -> new EmailContent(
                    business + " fue aprobado",
                    "¡Tu emprendimiento fue aprobado!",
                    List.of(greeting + "revisamos " + business + " y ya forma parte de Uruguay Mnatural.",
                            "Ya podés iniciar sesión y empezar a publicar tus actividades."),
                    null, null,
                    "Iniciar sesión", emailSender.url("/login"));
            case DENIED -> new EmailContent(
                    "Resultado de la solicitud de " + business,
                    "No pudimos aprobar tu solicitud",
                    List.of(greeting + "revisamos la solicitud de " + business + " y por ahora no pudimos aprobarla.",
                            "Si tenés consultas o querés volver a postularte con los datos corregidos, "
                                    + "respondé a este correo."),
                    "Motivo", event.reason(), null, null);
            case SUSPENDED -> new EmailContent(
                    business + " fue suspendido",
                    "Tu emprendimiento fue suspendido",
                    List.of(greeting + "el emprendimiento " + business + " fue suspendido en Uruguay Mnatural.",
                            "Mientras esté suspendido, sus actividades no se mostrarán en el portal y no vas a "
                                    + "poder publicar nuevas. Si tenés consultas, respondé a este correo."),
                    "Motivo", event.reason(), null, null);
            case REACTIVATED -> new EmailContent(
                    business + " fue reactivado",
                    "Tu emprendimiento fue reactivado",
                    List.of(greeting + "el emprendimiento " + business + " volvió a estar activo en Uruguay Mnatural.",
                            "Sus actividades vuelven a mostrarse en el portal y ya podés publicar nuevamente."),
                    null, null,
                    "Iniciar sesión", emailSender.url("/login"));
        };
    }
}
