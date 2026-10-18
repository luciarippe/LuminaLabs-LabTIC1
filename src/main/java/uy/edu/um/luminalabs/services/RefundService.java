package uy.edu.um.luminalabs.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.luminalabs.entities.Payment;
import uy.edu.um.luminalabs.entities.PaymentStatus;
import uy.edu.um.luminalabs.entities.PaymentType;
import uy.edu.um.luminalabs.events.RefundRequestedEvent;
import uy.edu.um.luminalabs.payments.GatewayResult;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentGateway;
import uy.edu.um.luminalabs.payments.RefundRequest;
import uy.edu.um.luminalabs.repositories.PaymentRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Devoluciones con la pasarela (RF-19, CU-05 pasos 7 y 8, CU-18 paso 4).
// Mismo esquema que el pago: leer en una transaccion, llamar a la pasarela fuera, guardar en otra.
// A diferencia de un cobro, reintentar una devolucion es seguro: va siempre con la misma referencia y la
// pasarela no devuelve dos veces lo mismo. Por eso ante cualquier falla queda PENDING y se reintenta.
@Slf4j
@Service
public class RefundService {

    // Las devoluciones recien creadas las procesa onRefundRequested; el proceso automatico solo toma las
    // que siguen pendientes despues de este tiempo, para no procesar la misma dos veces a la vez.
    private static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String currency;

    public RefundService(PaymentRepository paymentRepository, PaymentGateway gateway,
                         TransactionTemplate transactionTemplate, Clock clock,
                         @Value("${app.payments.currency}") String currency) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.currency = currency;
    }

    // Pide la devolucion a la pasarela y devuelve como quedo
    public PaymentStatus processRefund(Long refundId) {
        // 1. Datos de la devolucion y del cobro original
        RefundRequest request = transactionTemplate.execute(status -> {
            Payment refund = paymentRepository.findById(refundId).orElseThrow();
            if (refund.getStatus() != PaymentStatus.PENDING) {
                return null;
            }
            Payment charge = paymentRepository.findByReservation_IdAndTypeAndStatus(
                    refund.getReservation().getId(), PaymentType.CHARGE, PaymentStatus.APPROVED).getFirst();
            return new RefundRequest(refund.getReference(), charge.getExternalId(), refund.getAmount(), currency);
        });
        if (request == null) {
            return currentStatus(refundId);
        }

        // 2. Pasarela, fuera de una transaccion
        GatewayResult result = gateway.refund(request);

        // 3. Resultado. La devolucion se bloquea por si otro proceso la esta guardando al mismo tiempo.
        return transactionTemplate.execute(status -> {
            Payment refund = paymentRepository.findByIdForUpdate(refundId).orElseThrow();
            if (refund.getStatus() == PaymentStatus.PENDING) {
                if (result.outcome() == Outcome.APPROVED) {
                    refund.approve(result.externalId(), LocalDateTime.now(clock));
                } else {
                    // 8a: queda pendiente y la reintenta el proceso automatico
                    log.warn("Refund {} not completed ({}), will retry", request.reference(), result.outcome());
                }
            }
            return refund.getStatus();
        });
    }

    // CU-18 paso 4. Cada devolucion se procesa por separado: si una falla, las demas siguen.
    public int retryPendingRefunds() {
        List<Payment> pending = paymentRepository.findByTypeAndStatusAndCreatedAtBefore(
                PaymentType.REFUND, PaymentStatus.PENDING, LocalDateTime.now(clock).minus(RETRY_AFTER));
        int approved = 0;
        for (Payment refund : pending) {
            try {
                if (processRefund(refund.getId()) == PaymentStatus.APPROVED) {
                    approved++;
                }
            } catch (RuntimeException e) {
                log.error("Could not retry refund {}", refund.getId(), e);
            }
        }
        return approved;
    }

    // Devoluciones que se registran dentro de otra operacion (ej. el prestador cancela un slot con 10 reservas):
    // se piden a la pasarela despues de confirmar esa transaccion y en segundo plano, sin hacer esperar al usuario.
    @Async
    @TransactionalEventListener
    public void onRefundRequested(RefundRequestedEvent event) {
        try {
            processRefund(event.refundPaymentId());
        } catch (RuntimeException e) {
            log.error("Could not process refund {}; the scheduler will retry", event.refundPaymentId(), e);
        }
    }

    private PaymentStatus currentStatus(Long refundId) {
        return paymentRepository.findById(refundId).map(Payment::getStatus).orElseThrow();
    }
}
