package uy.edu.um.luminalabs.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;

import java.net.SocketTimeoutException;
import java.time.Duration;

// Cliente HTTP de la pasarela de pagos (RNF-09: tolera errores, timeouts e indisponibilidad).
// Traduce cada situacion a un GatewayResult; el que decide que hacer con cada una es PaymentService.
@Slf4j
@Component
public class HttpPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public HttpPaymentGateway(@Value("${app.payments.base-url}") String baseUrl,
                              @Value("${app.payments.timeout-millis}") long timeoutMillis) {
        // Sin timeout, si la pasarela no responde el turista quedaria esperando para siempre
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    // API de la pasarela: POST /v1/payments
    @Override
    public GatewayResult charge(ChargeRequest request) {
        try {
            GatewayPayment response = restClient.post().uri("/v1/payments").body(request)
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
            return connectionProblem(request.reference(), e);
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
            return connectionProblem(request.reference(), e);
        }
    }

    // Un timeout NO significa que no se cobro: el pedido pudo llegar y procesarse sin que nos llegue la
    // respuesta. Por eso queda "sin respuesta" y despues se consulta. Si no se pudo ni conectar, no se cobro.
    private static GatewayResult connectionProblem(String reference, Exception e) {
        if (isTimeout(e)) {
            log.warn("Gateway timeout for {}", reference);
            return GatewayResult.of(Outcome.NO_RESPONSE);
        }
        log.warn("Gateway unreachable for {}: {}", reference, e.toString());
        return GatewayResult.of(Outcome.UNAVAILABLE);
    }

    // Busca un timeout en la excepcion o en sus causas (puede venir envuelto en otras excepciones)
    private static boolean isTimeout(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static GatewayResult toResult(GatewayPayment response) {
        if (response == null) {
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        Outcome outcome = "APPROVED".equals(response.status()) ? Outcome.APPROVED : Outcome.DECLINED;
        return new GatewayResult(outcome, response.id());
    }

    // Respuesta JSON de la pasarela
    record GatewayPayment(String id, String reference, String status) {
    }
}
