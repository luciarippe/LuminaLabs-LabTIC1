package uy.edu.um.luminalabs.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentCard.CardDetails;

import java.math.BigDecimal;

// Cliente HTTP de la pasarela de pagos SIMULADA (RNF-09: tolera errores, timeouts e indisponibilidad).
// Traduce cada situacion a un GatewayResult; el que decide que hacer con cada una es PaymentService.
// Se usa con app.payments.provider=mock (el valor por defecto); con Mercado Pago se usa MercadoPagoPaymentGateway.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock", matchIfMissing = true)
public class HttpPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public HttpPaymentGateway(@Value("${app.payments.base-url}") String baseUrl,
                              @Value("${app.payments.timeout-millis}") long timeoutMillis) {
        this.restClient = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(GatewayHttp.requestFactory(timeoutMillis)).build();
    }

    // API de la pasarela: POST /v1/payments
    @Override
    public GatewayResult charge(ChargeRequest request) {
        try {
            GatewayPayment response = restClient.post().uri("/v1/payments").body(toGatewayCharge(request))
                    .retrieve().body(GatewayPayment.class);
            return toResult(response);
        } catch (HttpClientErrorException e) {
            // 4xx: la pasarela rechazo el pedido (ej. tarjeta invalida)
            log.info("Gateway rejected {}: {}", request, e.getStatusCode());
            return GatewayResult.of(Outcome.DECLINED);
        } catch (HttpServerErrorException e) {
            log.warn("Gateway error for {}: {}", request, e.getStatusCode());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            // Cualquier otro problema de conexion. Se atrapa Exception y no solo ResourceAccessException porque
            // segun en que momento ocurra, el timeout puede llegar como SocketTimeoutException sin envolver.
            return GatewayHttp.connectionProblem(request.reference(), e);
        }
    }

    // API de la pasarela: GET /v1/payments/{reference}
    @Override
    public GatewayResult findByReference(String reference) {
        try {
            GatewayPayment response = restClient.get().uri("/v1/payments/{reference}", reference)
                    .retrieve().body(GatewayPayment.class);
            return toResult(response);
        } catch (HttpClientErrorException e) {
            return e.getStatusCode() == HttpStatus.NOT_FOUND
                    ? GatewayResult.of(Outcome.NOT_FOUND)
                    : GatewayResult.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            // Si no podemos preguntar, no sabemos: no se puede dar el pago por aprobado ni por rechazado
            log.warn("Could not query gateway for {}: {}", reference, e.toString());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
    }

    // API de la pasarela: POST /v1/refunds. Cualquier falla deja la devolucion pendiente para reintentarla.
    @Override
    public GatewayResult refund(RefundRequest request) {
        try {
            GatewayPayment response = restClient.post().uri("/v1/refunds").body(request)
                    .retrieve().body(GatewayPayment.class);
            return toResult(response);
        } catch (HttpClientErrorException e) {
            log.warn("Gateway rejected refund {}: {}", request.reference(), e.getStatusCode());
            return GatewayResult.of(Outcome.DECLINED);
        } catch (HttpServerErrorException e) {
            log.warn("Gateway error for refund {}: {}", request.reference(), e.getStatusCode());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            return GatewayHttp.connectionProblem(request.reference(), e);
        }
    }

    // La pasarela simulada recibe los datos de la tarjeta tal cual (no genera tokens como Mercado Pago)
    private static GatewayCharge toGatewayCharge(ChargeRequest request) {
        if (!(request.card() instanceof CardDetails card)) {
            throw new IllegalArgumentException("The mock gateway needs the card details, got " + request.card());
        }
        return new GatewayCharge(request.reference(), request.amount(), request.currency(),
                card.number(), card.holder(), card.expiry(), card.cvv());
    }

    private static GatewayResult toResult(GatewayPayment response) {
        if (response == null) {
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        Outcome outcome = "APPROVED".equals(response.status()) ? Outcome.APPROVED : Outcome.DECLINED;
        return new GatewayResult(outcome, response.id());
    }

    // Pedido de cobro en JSON
    record GatewayCharge(String reference, BigDecimal amount, String currency,
                         String cardNumber, String cardHolder, String expiry, String cvv) {
    }

    // Respuesta JSON de la pasarela
    record GatewayPayment(String id, String reference, String status) {
    }
}
