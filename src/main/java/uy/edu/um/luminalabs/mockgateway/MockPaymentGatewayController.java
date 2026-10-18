package uy.edu.um.luminalabs.mockgateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// PASARELA DE PAGOS SIMULADA. Representa al servicio externo de pagos (un sistema de otra empresa).
// Vive en este proyecto solo para poder probar sin una pasarela real; la aplicacion la usa unicamente por
// HTTP, a traves de HttpPaymentGateway, igual que usaria una real. No comparte codigo ni base de datos.
//
// Como las pasarelas reales en "modo de prueba", responde segun el numero de tarjeta (ver CARDS).
// Guarda los pagos en memoria: se pierden al reiniciar la aplicacion.
@Slf4j
@RestController
@RequestMapping("/mock-gateway/v1")
public class MockPaymentGatewayController {

    // Tarjetas de prueba (tambien se muestran en la pantalla de pago)
    static final String DECLINED_CARD = "4000000000000002";
    static final String ERROR_CARD = "4000000000000119";
    static final String TIMEOUT_APPROVED_CARD = "4000000000003220";
    static final String TIMEOUT_DECLINED_CARD = "4000000000003246";
    // El cobro se aprueba, pero las devoluciones de ese cobro fallan las primeras veces (para ver los reintentos)
    static final String FAILING_REFUNDS_CARD = "4000000000000069";
    private static final int REFUND_FAILURES = 2;

    private final Map<String, StoredPayment> paymentsByReference = new ConcurrentHashMap<>();
    // Cobros cuyas devoluciones van a fallar, por id de cobro
    private final Map<String, Boolean> failingRefundCharges = new ConcurrentHashMap<>();
    private final Map<String, Integer> refundAttempts = new ConcurrentHashMap<>();
    private final long slowResponseMillis;

    public MockPaymentGatewayController(@Value("${app.mock-gateway.slow-response-millis}") long slowResponseMillis) {
        this.slowResponseMillis = slowResponseMillis;
    }

    @PostMapping("/payments")
    public ResponseEntity<StoredPayment> charge(@RequestBody PaymentRequest request) throws InterruptedException {
        // Idempotencia: la misma referencia devuelve el resultado anterior y no cobra de nuevo
        StoredPayment existing = paymentsByReference.get(request.reference());
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }
        String card = request.cardNumber() == null ? "" : request.cardNumber().replaceAll("\\D", "");
        if (card.equals(ERROR_CARD)) {
            // Falla interna: el pedido no se procesa ni se guarda
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        boolean approved = !card.equals(DECLINED_CARD) && !card.equals(TIMEOUT_DECLINED_CARD);
        StoredPayment payment = new StoredPayment(UUID.randomUUID().toString(), request.reference(),
                approved ? "APPROVED" : "DECLINED", request.amount());
        paymentsByReference.put(request.reference(), payment);
        if (card.equals(FAILING_REFUNDS_CARD)) {
            failingRefundCharges.put(payment.id(), true);
        }
        log.info("[mock-gateway] {} {} {}", request.reference(), payment.status(), request.amount());

        if (card.equals(TIMEOUT_APPROVED_CARD) || card.equals(TIMEOUT_DECLINED_CARD)) {
            // El pago YA quedo procesado, pero la respuesta tarda mas que el timeout del cliente:
            // la aplicacion no se entera del resultado y tiene que consultarlo despues.
            Thread.sleep(slowResponseMillis);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/payments/{reference}")
    public ResponseEntity<StoredPayment> findByReference(@PathVariable String reference) {
        StoredPayment payment = paymentsByReference.get(reference);
        return payment == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(payment);
    }

    // Devoluciones. Como los cobros, son idempotentes por referencia. Se aceptan aunque el cobro original no
    // este en memoria (la pasarela simulada lo pierde al reiniciar la aplicacion).
    @PostMapping("/refunds")
    public ResponseEntity<StoredPayment> refund(@RequestBody RefundRequest request) {
        StoredPayment existing = paymentsByReference.get(request.reference());
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }
        if (failingRefundCharges.containsKey(request.chargeExternalId())
                && refundAttempts.merge(request.reference(), 1, Integer::sum) <= REFUND_FAILURES) {
            log.info("[mock-gateway] refund {} failed on purpose", request.reference());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        StoredPayment refund = new StoredPayment(UUID.randomUUID().toString(), request.reference(), "APPROVED", request.amount());
        paymentsByReference.put(request.reference(), refund);
        log.info("[mock-gateway] refund {} APPROVED {}", request.reference(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }

    record RefundRequest(String reference, String chargeExternalId, BigDecimal amount, String currency) {
    }

    record PaymentRequest(String reference, BigDecimal amount, String currency,
                          String cardNumber, String cardHolder, String expiry, String cvv) {
    }

    record StoredPayment(String id, String reference, String status, BigDecimal amount) {
    }
}
