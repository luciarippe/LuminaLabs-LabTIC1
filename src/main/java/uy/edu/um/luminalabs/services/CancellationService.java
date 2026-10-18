package uy.edu.um.luminalabs.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.RefundRequestedEvent;
import uy.edu.um.luminalabs.events.ReservationCancelledEvent;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.repositories.CancellationRepository;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.services.CancellationQuote.Case;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Cancelacion de reservas (RF-19, CU-05): por el turista, con la politica de la actividad; o por la plataforma
// cuando el prestador cancela un slot (CU-04 5b) o se suspende el emprendimiento (RF-06), con devolucion total.
// La devolucion se registra como un Payment de tipo REFUND y la procesa RefundService.
@Service
public class CancellationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final CancellationRepository cancellationRepository;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancellationService(ReservationRepository reservationRepository, PaymentRepository paymentRepository,
                               CancellationRepository cancellationRepository, RefundService refundService,
                               TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.cancellationRepository = cancellationRepository;
        this.refundService = refundService;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ---------- Cancelacion por el turista (CU-05) ----------

    // Pasos 1 y 2: lo que se le muestra al turista antes de confirmar
    @Transactional(readOnly = true)
    public CancellationQuote quote(Long touristId, Long reservationId) {
        Reservation reservation = findOwn(touristId, reservationId);
        LocalDateTime now = LocalDateTime.now(clock);
        requireCancellableByTourist(reservation, now);
        return touristQuote(reservation, now);
    }

    // Pasos 3 a 9. La cancelacion se guarda en una transaccion y despues se pide la devolucion a la pasarela,
    // fuera de la transaccion (igual que en el pago). Si la devolucion falla, la reserva queda cancelada igual
    // y la devolucion pendiente se reintenta (8a).
    public Cancellation cancelByTourist(Long touristId, Long reservationId) {
        Long refundId = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                    .filter(found -> found.getTourist().getId().equals(touristId))
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
            requireCancellableByTourist(reservation, now);
            Payment refund = cancel(reservation, CancellationReason.TOURIST, touristQuote(reservation, now), now);
            return refund == null ? null : refund.getId();
        });
        if (refundId != null) {
            refundService.processRefund(refundId);
        }
        return findCancellation(reservationId).orElseThrow();
    }

    public Optional<Cancellation> findCancellation(Long reservationId) {
        return cancellationRepository.findByReservation_Id(reservationId);
    }

    // CU-05 4a, 4b y RF-10
    CancellationQuote touristQuote(Reservation reservation, LocalDateTime now) {
        BigDecimal paid = amountPaid(reservation);
        if (paid.signum() == 0) {
            return new CancellationQuote(Case.NOT_PAID, paid, ZERO, ZERO);
        }
        if (reservation.isScheduleChanged()) {
            return new CancellationQuote(Case.SCHEDULE_CHANGED, paid, ZERO, paid);
        }
        Activity activity = reservation.getSlot().getActivity();
        LocalDateTime deadline = reservation.getSlot().getStartTime().minusHours(activity.getCancellationDeadlineHours());
        if (now.isAfter(deadline)) {
            return new CancellationQuote(Case.OUTSIDE_DEADLINE, paid, paid, ZERO);
        }
        BigDecimal fee = paid.multiply(BigDecimal.valueOf(activity.getCancellationFeePercentage()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new CancellationQuote(Case.WITHIN_DEADLINE, paid, fee, paid.subtract(fee));
    }

    private void requireCancellableByTourist(Reservation reservation, LocalDateTime now) {
        if (!reservation.isActive()) {
            throw new IllegalStateException("Esta reserva está " + reservation.getStatus().getLabel().toLowerCase()
                    + " y ya no se puede cancelar.");
        }
        if (reservation.getSlot().hasStarted(now)) {
            throw new IllegalStateException("La actividad ya comenzó y la reserva no se puede cancelar.");
        }
        // Con un cobro sin respuesta no sabemos si el turista pago: no se puede calcular la devolucion
        if (!paymentRepository.findByReservation_IdAndTypeAndStatus(reservation.getId(), PaymentType.CHARGE,
                PaymentStatus.NO_RESPONSE).isEmpty()) {
            throw new IllegalStateException("Tenés un pago sin confirmar. Verificalo antes de cancelar la reserva.");
        }
    }

    // ---------- Cancelacion por la plataforma (CU-04 5b, RF-06) ----------

    // Se ejecuta dentro de la transaccion de quien llama (cancelar el slot, suspender el emprendimiento).
    // Las devoluciones se piden a la pasarela despues, en segundo plano (ver RefundService.onRefundRequested).
    @Transactional
    public int cancelWithFullRefund(List<Reservation> reservations, CancellationReason reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        int cancelled = 0;
        for (Reservation reservation : reservations) {
            if (!reservation.isActive()) {
                continue;
            }
            BigDecimal paid = amountPaid(reservation);
            Payment refund = cancel(reservation, reason, new CancellationQuote(Case.CANCELLED_BY_PLATFORM, paid, ZERO, paid), now);
            if (refund != null) {
                eventPublisher.publishEvent(new RefundRequestedEvent(refund.getId()));
            }
            cancelled++;
        }
        return cancelled;
    }

    // ---------- Comun ----------

    // Pasos 6 y 7: cancela la reserva (devolviendo los lugares), registra la cancelacion y, si corresponde,
    // la devolucion pendiente. Devuelve la devolucion o null si no hay nada que devolver.
    private Payment cancel(Reservation reservation, CancellationReason reason, CancellationQuote quote, LocalDateTime now) {
        reservation.cancel();
        Payment refund = null;
        if (quote.hasRefund()) {
            Payment charge = approvedCharge(reservation).orElseThrow();
            refund = paymentRepository.save(Payment.refundOf(charge, quote.refund(), now));
        }
        cancellationRepository.save(new Cancellation(reservation, reason, quote.fee(), quote.refund(), refund, now));

        Slot slot = reservation.getSlot();
        List<MailRecipient> providers = slot.getActivity().getBusiness().getProviders().stream()
                .map(MailRecipient::from).toList();
        eventPublisher.publishEvent(new ReservationCancelledEvent(MailRecipient.from(reservation.getTourist()), providers,
                reservation.getId(), reservation.getCode(), slot.getActivity().getName(), slot.getStartTime(),
                reservation.getParticipantCount(), reason, quote.refund()));
        return refund;
    }

    // Lo cobrado: el cobro aprobado de la reserva (si esta pendiente de pago, nada)
    private BigDecimal amountPaid(Reservation reservation) {
        return approvedCharge(reservation).map(Payment::getAmount).orElse(ZERO);
    }

    private Optional<Payment> approvedCharge(Reservation reservation) {
        return paymentRepository.findByReservation_IdAndTypeAndStatus(reservation.getId(), PaymentType.CHARGE,
                PaymentStatus.APPROVED).stream().findFirst();
    }

    private Reservation findOwn(Long touristId, Long reservationId) {
        return reservationRepository.findWithDetailsById(reservationId)
                .filter(reservation -> reservation.getTourist().getId().equals(touristId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
