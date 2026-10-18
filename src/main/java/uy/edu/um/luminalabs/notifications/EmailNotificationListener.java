package uy.edu.um.luminalabs.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.luminalabs.entities.CancellationReason;
import uy.edu.um.luminalabs.events.BusinessRegisteredEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.ReservationCancelledEvent;
import uy.edu.um.luminalabs.events.ReservationConfirmedEvent;
import uy.edu.um.luminalabs.events.ReservationExpiredEvent;
import uy.edu.um.luminalabs.events.SlotRescheduledEvent;
import uy.edu.um.luminalabs.events.TouristRegisteredEvent;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Notificaciones por correo (RF-26). @TransactionalEventListener: el correo se envia solo si la
// transaccion se confirmo, asi nunca se avisa de un cambio que termino deshaciendose.
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-UY"));

    // DecimalFormat no se puede compartir entre hilos (y los correos se mandan en varios a la vez, @Async)
    private static String money(BigDecimal amount) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY"))).format(amount);
    }

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
    public void onReservationConfirmed(ReservationConfirmedEvent event) {
        MailRecipient recipient = event.recipient();
        emailSender.send(recipient, new EmailContent(
                "Reserva " + event.reservationCode() + " confirmada",
                "¡Tu reserva está confirmada!",
                List.of("Hola " + recipient.firstName() + ", recibimos el pago de $ " + money(event.amount())
                                + " y tu reserva para " + event.activityName() + " quedó confirmada.",
                        "Te esperamos el " + event.startTime().format(DATE_TIME) + " en " + event.address() + "."),
                "Código de reserva", event.reservationCode(),
                "Ver mi reserva", emailSender.url("/reservations/" + event.reservationId())));
    }

    // CU-05 paso 9: al turista y a los prestadores. El texto depende de quien cancelo.
    @Async
    @TransactionalEventListener
    public void onReservationCancelled(ReservationCancelledEvent event) {
        MailRecipient tourist = event.tourist();
        String when = event.startTime().format(DATE_TIME);
        String refund = event.refundAmount().signum() > 0
                ? "Te devolvemos $ " + money(event.refundAmount()) + " a la misma tarjeta con la que pagaste."
                : "No corresponde devolución.";
        String what = switch (event.reason()) {
            case TOURIST -> "cancelaste tu reserva para " + event.activityName() + " del " + when + ".";
            case SLOT_CANCELLED -> "el organizador canceló " + event.activityName() + " del " + when
                    + ", así que tu reserva quedó cancelada.";
            case BUSINESS_SUSPENDED -> "el emprendimiento que organiza " + event.activityName() + " del " + when
                    + " fue suspendido, así que tu reserva quedó cancelada.";
        };
        emailSender.send(tourist, new EmailContent(
                "Reserva " + event.reservationCode() + " cancelada",
                "Tu reserva fue cancelada",
                List.of("Hola " + tourist.firstName() + ", " + what, refund),
                "Código de reserva", event.reservationCode(),
                "Ver mi reserva", emailSender.url("/reservations/" + event.reservationId())));

        // A los prestadores solo cuando cancela el turista (en los otros casos la cancelacion la causaron ellos)
        if (event.reason() == CancellationReason.TOURIST) {
            for (MailRecipient provider : event.providers()) {
                emailSender.send(provider, new EmailContent(
                        "Se canceló la reserva " + event.reservationCode(),
                        "Se canceló una reserva",
                        List.of("Hola " + provider.firstName() + ", el turista canceló la reserva " + event.reservationCode()
                                        + " (" + event.participantCount() + " personas) para " + event.activityName()
                                        + " del " + when + ".",
                                "Los lugares ya volvieron a estar disponibles para otros turistas."),
                        null, null, null, null));
            }
        }
    }

    // CU-18 paso 2
    @Async
    @TransactionalEventListener
    public void onReservationExpired(ReservationExpiredEvent event) {
        MailRecipient recipient = event.recipient();
        emailSender.send(recipient, new EmailContent(
                "Reserva " + event.reservationCode() + " vencida",
                "Tu reserva venció",
                List.of("Hola " + recipient.firstName() + ", la reserva " + event.reservationCode() + " para "
                                + event.activityName() + " venció porque no recibimos el pago dentro de las 2 horas.",
                        "No se te cobró nada. Si todavía querés ir, podés volver a reservar."),
                null, null,
                "Ver la actividad", emailSender.url("/activities/" + event.activityId())));
    }

    // CU-04 5a: el turista puede cancelar con devolucion total (CU-05 4b)
    @Async
    @TransactionalEventListener
    public void onSlotRescheduled(SlotRescheduledEvent event) {
        MailRecipient recipient = event.recipient();
        emailSender.send(recipient, new EmailContent(
                "Cambio de horario en tu reserva " + event.reservationCode(),
                "Cambió el horario de tu actividad",
                List.of("Hola " + recipient.firstName() + ", el organizador de " + event.activityName()
                                + " cambió el horario de tu reserva.",
                        "Antes: " + event.previousStart().format(DATE_TIME) + ". Ahora: " + event.newStart().format(DATE_TIME) + ".",
                        "Si el nuevo horario no te sirve, podés cancelar la reserva con devolución total."),
                "Nuevo horario", event.newStart().format(DATE_TIME),
                "Ver mi reserva", emailSender.url("/reservations/" + event.reservationId())));
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
                            "Podés volver a postularlo con los datos corregidos desde el formulario de registro, "
                                    + "usando el mismo RUT o cédula y los datos de tu cuenta. Si tenés consultas, "
                                    + "respondé a este correo."),
                    "Motivo", event.reason(),
                    "Volver a postular", emailSender.url("/register/provider"));
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
