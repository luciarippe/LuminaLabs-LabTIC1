package uy.edu.um.luminalabs.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.luminalabs.dto.CardForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.RefundRequestedEvent;
import uy.edu.um.luminalabs.events.ReservationConfirmedEvent;
import uy.edu.um.luminalabs.events.ReservationExpiredEvent;
import uy.edu.um.luminalabs.exceptions.PaymentNotPossibleException;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.payments.ChargeRequest;
import uy.edu.um.luminalabs.payments.GatewayResult;
import uy.edu.um.luminalabs.payments.PaymentGateway;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Pago de reservas con la pasarela externa: CU-01 pasos 7 a 10 y CU-16. Reglas de RF-18:
//  - nunca se confirma una reserva sin un pago aprobado;
//  - cada intento queda registrado con su monto, fecha y estado;
//  - si un intento quedo "sin respuesta", antes de cobrar de nuevo se le pregunta a la pasarela.
//
// Un pago tiene TRES pasos, cada uno con su propia transaccion (por eso TransactionTemplate y no @Transactional):
//   1. Registrar el intento ("sin respuesta") y confirmar la transaccion. Si la aplicacion se cae mientras espera
//      a la pasarela, queda constancia de que el cobro pudo haberse hecho.
//   2. Llamar a la pasarela FUERA de una transaccion: no se deja la base bloqueada esperando a otro sistema.
//   3. Guardar el resultado (y confirmar la reserva si se aprobo).
@Slf4j
@Service
public class PaymentService {

    public enum Result { CONFIRMED, REJECTED, NO_RESPONSE }

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String currency;
    // Mientras un intento puede estar todavia en camino (dura como mucho el timeout), no se lo consulta:
    // la pasarela podria no haberlo recibido aun y diriamos "no se cobro" cuando si se va a cobrar.
    private final Duration inFlightWindow;

    public PaymentService(ReservationRepository reservationRepository, PaymentRepository paymentRepository,
                          PaymentGateway gateway, TransactionTemplate transactionTemplate,
                          ApplicationEventPublisher eventPublisher, Clock clock,
                          @Value("${app.payments.currency}") String currency,
                          @Value("${app.payments.timeout-millis}") long timeoutMillis) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.currency = currency;
        this.inFlightWindow = Duration.ofMillis(timeoutMillis).plusSeconds(2);
    }

    public Result pay(Long touristId, Long reservationId, CardForm card) {
        // Paso 1 (transaccion): validar, resolver intentos anteriores y registrar el nuevo
        Attempt attempt = transactionTemplate.execute(status -> startAttempt(touristId, reservationId, card));
        if (attempt.result() != null) {
            return attempt.result();
        }
        if (attempt.paymentId() == null) {
            // 4b: la transaccion del paso 1 ya dejo la reserva vencida; recien ahora se avisa al turista
            throw new PaymentNotPossibleException("Este horario ya no admite reservas, así que la reserva venció "
                    + "y liberamos los lugares. No se te cobró nada.");
        }
        // Paso 2 (sin transaccion): llamar a la pasarela
        GatewayResult gatewayResult = gateway.charge(new ChargeRequest(attempt.reference(), attempt.amount(), currency,
                card.getCardDigits(), card.getCardHolder().trim(), card.getExpiry(), card.getCvv()));
        // Paso 3 (transaccion): guardar el resultado
        return transactionTemplate.execute(status -> recordResult(attempt.paymentId(), gatewayResult));
    }

    // CU-16 5a a pedido del turista ("Verificar el pago"): pregunta a la pasarela por los intentos sin respuesta
    public Result checkPendingPayment(Long touristId, Long reservationId) {
        return transactionTemplate.execute(status -> {
            Reservation reservation = lockOwnReservation(touristId, reservationId);
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                return Result.CONFIRMED;
            }
            if (!reservation.isPendingPayment()) {
                return Result.REJECTED;
            }
            return switch (resolveAwaitingPayments(reservation, LocalDateTime.now(clock))) {
                case CONFIRMED -> Result.CONFIRMED;
                case NONE_PENDING -> Result.REJECTED;
                case UNKNOWN, IN_PROGRESS -> Result.NO_RESPONSE;
            };
        });
    }

    // CU-13 paso 5: los intentos de pago de la reserva
    public List<Payment> findPayments(Long reservationId) {
        return paymentRepository.findByReservation_IdOrderByCreatedAtAsc(reservationId);
    }

    // ---------- Proceso automatico (CU-18) ----------

    // Pasos 1 y 2: ids de las reservas pendientes cuyo plazo de pago ya vencio
    public List<Long> findReservationsPastPaymentDeadline() {
        return reservationRepository.findIdsByStatusAndCreatedAtBefore(ReservationStatus.PENDING_PAYMENT,
                LocalDateTime.now(clock).minus(Reservation.PAYMENT_WINDOW));
    }

    // Paso 2: vence la reserva y libera sus lugares, salvo que tenga un cobro sin respuesta:
    //  2a. se consulta a la pasarela; si se habia aprobado, la reserva se confirma en vez de vencer.
    //  2b. si la pasarela no responde, queda pendiente hasta la proxima ejecucion (podria estar paga).
    // Devuelve el estado en que quedo la reserva.
    public ReservationStatus expireIfUnpaid(Long reservationId) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElseThrow();
            if (!reservation.isPendingPayment() || reservation.isPaymentWindowOpen(now)) {
                return reservation.getStatus();
            }
            if (resolveAwaitingPayments(reservation, now) == Resolution.NONE_PENDING) {
                reservation.expire();
                eventPublisher.publishEvent(new ReservationExpiredEvent(MailRecipient.from(reservation.getTourist()),
                        reservation.getCode(), reservation.getSlot().getActivity().getId(),
                        reservation.getSlot().getActivity().getName()));
            }
            return reservation.getStatus();
        });
    }

    public List<Long> findOrphanCharges() {
        return paymentRepository.findOrphanChargeIds(LocalDateTime.now(clock).minus(inFlightWindow));
    }

    // Un cobro sin respuesta de una reserva que ya no esta pendiente (la cancelo la plataforma mientras tanto).
    // Si la pasarela lo habia aprobado, el turista pago una reserva que no tiene: se le devuelve todo.
    public void resolveOrphanCharge(Long paymentId) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Payment charge = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            if (!charge.isAwaitingResponse()) {
                return;
            }
            GatewayResult result = gateway.findByReference(charge.getReference());
            switch (result.outcome()) {
                case APPROVED -> {
                    charge.approve(result.externalId(), now);
                    refundLateCharge(charge, now);
                }
                case DECLINED, NOT_FOUND -> charge.reject(result.externalId(), now);
                case UNAVAILABLE, NO_RESPONSE -> {
                    // se vuelve a intentar en la proxima ejecucion
                }
            }
        });
    }

    // Devolucion total de un cobro que se aprobo cuando la reserva ya no estaba pendiente
    private void refundLateCharge(Payment charge, LocalDateTime now) {
        log.warn("Charge {} approved for reservation {} in status {}: refunding it", charge.getReference(),
                charge.getReservation().getId(), charge.getReservation().getStatus());
        Payment refund = paymentRepository.save(Payment.refundOf(charge, charge.getAmount(), now));
        eventPublisher.publishEvent(new RefundRequestedEvent(refund.getId()));
    }

    // ---------- Paso 1 ----------

    private Attempt startAttempt(Long touristId, Long reservationId, CardForm card) {
        LocalDateTime now = LocalDateTime.now(clock);
        // Bloquea la reserva: si llegan dos pedidos de pago a la vez, el segundo espera al primero
        Reservation reservation = lockOwnReservation(touristId, reservationId);
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new PaymentNotPossibleException("Esta reserva ya está paga.");
        }
        if (!reservation.isPendingPayment()) {
            throw new PaymentNotPossibleException("Esta reserva está " + reservation.getStatus().getLabel().toLowerCase()
                    + " y ya no se puede pagar.");
        }

        // 5a. Primero se resuelven los intentos sin respuesta (antes que el plazo: si uno se cobro, la reserva
        // se confirma aunque el plazo ya haya vencido, porque el turista pago a tiempo).
        switch (resolveAwaitingPayments(reservation, now)) {
            case CONFIRMED -> {
                return Attempt.finished(Result.CONFIRMED);
            }
            case IN_PROGRESS -> throw new PaymentNotPossibleException(
                    "Tu pago anterior todavía se está procesando. Esperá unos segundos y revisá el estado de la reserva.");
            case UNKNOWN -> throw new PaymentNotPossibleException(
                    "Todavía no pudimos confirmar con la pasarela qué pasó con tu pago anterior. Para no cobrarte "
                            + "dos veces, no podemos intentar otro pago hasta saberlo. Probá de nuevo en unos minutos.");
            case NONE_PENDING -> {
            }
        }

        // 4a. Plazo de 2 horas para pagar
        if (!reservation.isPaymentWindowOpen(now)) {
            throw new PaymentNotPossibleException("Se venció el plazo para pagar esta reserva.");
        }
        // 4b. El slot ya no admite reservas: la reserva vence y libera sus lugares.
        // Se devuelve en vez de lanzar una excepcion para que la transaccion se confirme con el cambio.
        if (!reservation.getSlot().isWithinBookingWindow(now)) {
            reservation.expire();
            return Attempt.finished(null);
        }

        Payment payment = paymentRepository.save(new Payment(reservation, PaymentType.CHARGE,
                reservation.getTotalAmount(), card.getCardHolder().trim(), card.getLastDigits(), now));
        return Attempt.ready(payment.getId(), payment.getReference(), payment.getAmount());
    }

    // ---------- Paso 3 ----------

    private Result recordResult(Long paymentId, GatewayResult gatewayResult) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        Reservation reservation = reservationRepository.findByIdForUpdate(payment.getReservation().getId()).orElseThrow();

        return switch (gatewayResult.outcome()) {
            case APPROVED -> {
                payment.approve(gatewayResult.externalId(), now);
                if (!reservation.isPendingPayment()) {
                    // Mientras esperabamos a la pasarela la plataforma cancelo la reserva (ej. se cancelo el slot)
                    refundLateCharge(payment, now);
                    yield Result.REJECTED;
                }
                confirm(reservation);
                yield Result.CONFIRMED;
            }
            // 9a: rechazado o pasarela no disponible. La reserva sigue pendiente, con sus lugares, hasta el plazo.
            case DECLINED, UNAVAILABLE, NOT_FOUND -> {
                payment.reject(gatewayResult.externalId(), now);
                yield Result.REJECTED;
            }
            // 9b: timeout. El intento queda "sin respuesta" hasta que se consulte a la pasarela.
            case NO_RESPONSE -> Result.NO_RESPONSE;
        };
    }

    // ---------- Intentos sin respuesta (RF-18, CU-16 5a, CU-18 2a) ----------

    private enum Resolution { NONE_PENDING, CONFIRMED, UNKNOWN, IN_PROGRESS }

    // Le pregunta a la pasarela por cada intento sin respuesta. Se hace con la reserva bloqueada para que
    // nadie mas intente cobrar mientras tanto; es una consulta corta y solo bloquea esta reserva.
    private Resolution resolveAwaitingPayments(Reservation reservation, LocalDateTime now) {
        List<Payment> awaiting = paymentRepository.findByReservation_IdAndTypeAndStatus(
                reservation.getId(), PaymentType.CHARGE, PaymentStatus.NO_RESPONSE);
        for (Payment payment : awaiting) {
            if (now.isBefore(payment.getCreatedAt().plus(inFlightWindow))) {
                return Resolution.IN_PROGRESS;
            }
            GatewayResult result = gateway.findByReference(payment.getReference());
            switch (result.outcome()) {
                case APPROVED -> {
                    // Se habia cobrado: se confirma sin volver a cobrar
                    payment.approve(result.externalId(), now);
                    confirm(reservation);
                    return Resolution.CONFIRMED;
                }
                // Rechazado, o la pasarela nunca lo recibio: no se cobro
                case DECLINED, NOT_FOUND -> payment.reject(result.externalId(), now);
                case UNAVAILABLE, NO_RESPONSE -> {
                    return Resolution.UNKNOWN;
                }
            }
        }
        return Resolution.NONE_PENDING;
    }

    // Solo se llama con la reserva pendiente de pago (si no, Reservation.confirm lanza una excepcion)
    private void confirm(Reservation reservation) {
        reservation.confirm();
        Slot slot = reservation.getSlot();
        Activity activity = slot.getActivity();
        // Se envia despues de confirmar la transaccion (ver EmailNotificationListener)
        eventPublisher.publishEvent(new ReservationConfirmedEvent(MailRecipient.from(reservation.getTourist()),
                reservation.getId(), reservation.getCode(), activity.getName(), slot.getStartTime(),
                activity.getAddress() + " — " + activity.getLocation().getName(), reservation.getTotalAmount()));
    }

    // RNF-10: la reserva de otro turista se trata como inexistente
    private Reservation lockOwnReservation(Long touristId, Long reservationId) {
        return reservationRepository.findByIdForUpdate(reservationId)
                .filter(reservation -> reservation.getTourist().getId().equals(touristId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    // Resultado del paso 1: o ya hay un resultado final (result), o un intento listo para mandar a la pasarela
    // (paymentId). Si no hay ninguno de los dos, la reserva vencio en el paso 1 (4b).
    private record Attempt(Result result, Long paymentId, String reference, BigDecimal amount) {

        static Attempt finished(Result result) {
            return new Attempt(result, null, null, null);
        }

        static Attempt ready(Long paymentId, String reference, BigDecimal amount) {
            return new Attempt(null, paymentId, reference, amount);
        }
    }
}
