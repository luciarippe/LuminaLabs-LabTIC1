package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.entities.ReservationStatus;

import java.util.function.Consumer;

// CU-18: proceso automatico que corre cada app.scheduler.interval-millis (por defecto, cada minuto).
// Cada reserva o pago se procesa por separado, en su propia transaccion: si uno falla (ej. la pasarela no
// responde), los demas siguen y el que fallo se reintenta en la proxima ejecucion.
// En los tests esta apagado (app.scheduler.enabled=false) para que no cambie datos mientras se prueba.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationScheduler {

    private final PaymentService paymentService;
    private final ReservationService reservationService;
    private final RefundService refundService;

    @Scheduled(fixedDelayString = "${app.scheduler.interval-millis}", initialDelayString = "${app.scheduler.interval-millis}")
    public void run() {
        expireUnpaidReservations();
        int finished = reservationService.finishEndedReservations();
        int refunds = refundService.retryPendingRefunds();
        forEachSafely(paymentService.findOrphanCharges(), paymentService::resolveOrphanCharge, "orphan charge");
        if (finished > 0 || refunds > 0) {
            log.info("Scheduler: {} reservations finished, {} refunds approved", finished, refunds);
        }
    }

    // Pasos 1 y 2
    private void expireUnpaidReservations() {
        forEachSafely(paymentService.findReservationsPastPaymentDeadline(), id -> {
            ReservationStatus status = paymentService.expireIfUnpaid(id);
            log.info("Scheduler: reservation {} past payment deadline is now {}", id, status);
        }, "reservation");
    }

    private static void forEachSafely(Iterable<Long> ids, Consumer<Long> action, String what) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException e) {
                log.error("Scheduler could not process {} {}", what, id, e);
            }
        }
    }
}
