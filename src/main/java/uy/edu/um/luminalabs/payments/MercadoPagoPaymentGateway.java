package uy.edu.um.luminalabs.payments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentCard.CardToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Pasarela de pagos real: Mercado Pago, con su API de Orders (Checkout API). Con las credenciales de prueba
// funciona en modo sandbox: la API es la de verdad pero no se mueve dinero. Se usa con app.payments.provider=mercadopago
// (ver application-mercadopago.properties). Igual que HttpPaymentGateway, traduce cada respuesta a un GatewayResult
// y nunca lanza excepciones por fallas de la pasarela (RNF-09).
//
// Como se corresponde con lo que pide PaymentGateway:
//   charge          -> POST /v1/orders (la referencia va como external_reference y como X-Idempotency-Key)
//   findByReference -> GET  /v1/orders?external_reference=...
//   refund          -> POST /v1/orders/{id}/refund
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mercadopago")
public class MercadoPagoPaymentGateway implements PaymentGateway {

    // Mercado Pago no cobra ni devuelve dos veces un pedido con la misma clave
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    // La busqueda de orders exige un rango de fechas de 1 mes como maximo (si no, responde 400). Un intento sin
    // respuesta se consulta a los pocos minutos (o en la proxima ejecucion del proceso automatico), asi que
    // alcanza con mirar unos dias hacia atras. Se busca hasta un dia despues por si los relojes no coinciden.
    private static final Duration SEARCH_BACK = Duration.ofDays(25);
    private static final Duration SEARCH_AHEAD = Duration.ofDays(1);

    private final RestClient restClient;
    private final Clock clock;

    public MercadoPagoPaymentGateway(@Value("${app.mercadopago.base-url}") String baseUrl,
                                     @Value("${app.mercadopago.access-token}") String accessToken,
                                     @Value("${app.payments.timeout-millis}") long timeoutMillis, Clock clock) {
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Missing Mercado Pago access token: set the MERCADOPAGO_ACCESS_TOKEN environment variable");
        }
        // El Access Token identifica nuestra cuenta ante Mercado Pago: va en todos los pedidos y nunca al navegador
        this.restClient = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(GatewayHttp.requestFactory(timeoutMillis))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
        this.clock = clock;
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        if (!(request.card() instanceof CardToken card)) {
            throw new IllegalArgumentException("Mercado Pago needs a card token, got " + request.card());
        }
        String amount = format(request.amount());
        // processing_mode "automatic": Mercado Pago cobra en el mismo pedido y responde con el resultado
        OrderRequest body = new OrderRequest("online", "automatic", amount, request.reference(),
                new Payer(card.payerEmail()),
                new NewTransactions(List.of(new NewPayment(amount, new PaymentMethod(card.paymentMethodId(),
                        card.paymentTypeId(), card.token(), card.installments())))));
        try {
            Order order = restClient.post().uri("/v1/orders").header(IDEMPOTENCY_HEADER, request.reference())
                    .body(body).retrieve().body(Order.class);
            return toChargeResult(order);
        } catch (HttpClientErrorException e) {
            return rejectedCharge(request.reference(), e);
        } catch (HttpServerErrorException e) {
            log.warn("Mercado Pago error for {}: {}", request.reference(), e.getStatusCode());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            // Igual que en HttpPaymentGateway: el timeout puede llegar envuelto o sin envolver
            return GatewayHttp.connectionProblem(request.reference(), e);
        }
    }

    @Override
    public GatewayResult findByReference(String reference) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        try {
            OrderSearch result = restClient.get().uri(uri -> uri.path("/v1/orders")
                            .queryParam("external_reference", reference)
                            .queryParam("begin_date", now.minus(SEARCH_BACK).toString())
                            .queryParam("end_date", now.plus(SEARCH_AHEAD).toString())
                            .build())
                    .retrieve().body(OrderSearch.class);
            if (result == null || result.data() == null) {
                return GatewayResult.of(Outcome.UNAVAILABLE);
            }
            // Ninguna order con esa referencia: Mercado Pago nunca recibio el pedido, asi que no se cobro
            return result.data().isEmpty() ? GatewayResult.of(Outcome.NOT_FOUND) : toChargeResult(result.data().getFirst());
        } catch (Exception e) {
            // Si no podemos preguntar, no sabemos: no se puede dar el pago por aprobado ni por rechazado
            log.warn("Could not query Mercado Pago for {}: {}", reference, e.toString());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
    }

    // Cualquier falla deja la devolucion pendiente para reintentarla (RefundService). Como cada reintento usa la
    // misma clave de idempotencia, Mercado Pago no devuelve dos veces.
    @Override
    public GatewayResult refund(RefundRequest request) {
        try {
            // Para una devolucion parcial hay que indicar la transaccion de la order, asi que primero se consulta
            Order order = restClient.get().uri("/v1/orders/{id}", request.chargeExternalId())
                    .retrieve().body(Order.class);
            if (order == null || order.transactions() == null || order.transactions().payments() == null
                    || order.transactions().payments().isEmpty()) {
                log.warn("Mercado Pago order {} has no payments to refund", request.chargeExternalId());
                return GatewayResult.of(Outcome.UNAVAILABLE);
            }
            // Devolucion total: el cuerpo va vacio. Parcial: el monto y la transaccion.
            boolean total = request.amount().compareTo(new BigDecimal(order.totalAmount())) >= 0;
            RestClient.RequestBodySpec refundCall = restClient.post().uri("/v1/orders/{id}/refund", order.id())
                    .header(IDEMPOTENCY_HEADER, request.reference());
            if (!total) {
                refundCall.body(new RefundBody(List.of(new RefundTransaction(
                        order.transactions().payments().getFirst().id(), format(request.amount())))));
            }
            return toRefundResult(refundCall.retrieve().body(Order.class));
        } catch (HttpClientErrorException e) {
            log.warn("Mercado Pago rejected refund {}: {} {}", request.reference(), e.getStatusCode(),
                    e.getResponseBodyAsString());
            // 409: el cobro todavia tiene otra operacion en curso (ej. una devolucion anterior). Mercado Pago pide
            // reintentar en unos segundos. 429: demasiados pedidos.
            if (e.getStatusCode() == HttpStatus.CONFLICT || e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return GatewayResult.of(Outcome.UNAVAILABLE);
            }
            return GatewayResult.of(Outcome.DECLINED);
        } catch (HttpServerErrorException e) {
            log.warn("Mercado Pago error for refund {}: {}", request.reference(), e.getStatusCode());
            return GatewayResult.of(Outcome.UNAVAILABLE);
        } catch (Exception e) {
            return GatewayHttp.connectionProblem(request.reference(), e);
        }
    }

    // Un 4xx al cobrar suele ser un pago rechazado, y en ese caso Mercado Pago manda los errores y la order (con
    // estado "failed") en "data": {"errors":[...],"data":{"id":...,"status":"failed",...}}.
    // Pero tambien puede ser un problema nuestro (credenciales, datos invalidos), y ahi no viene la order.
    private static GatewayResult rejectedCharge(String reference, HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
            log.error("Mercado Pago refused our credentials for {}: check MERCADOPAGO_ACCESS_TOKEN", reference);
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Mercado Pago rate limit for {}", reference);
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        // El cuerpo no tiene datos de la tarjeta (solo el token), asi que se puede dejar en el log para entender el error
        log.info("Mercado Pago rejected {}: {} {}", reference, e.getStatusCode(), e.getResponseBodyAsString());
        try {
            OrderError error = e.getResponseBodyAs(OrderError.class);
            if (error != null && error.data() != null && error.data().status() != null) {
                return toChargeResult(error.data());
            }
        } catch (Exception ignored) {
            // El cuerpo no era una order (ej. un error de validacion): igual es un rechazo
        }
        return GatewayResult.of(Outcome.DECLINED);
    }

    // Estados de una order: ver "Estado de la order" en la documentacion de Mercado Pago
    private static GatewayResult toChargeResult(Order order) {
        if (order == null || order.status() == null) {
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        return switch (order.status()) {
            // Se cobro (aunque despues se haya devuelto o tenga un contracargo)
            case "processed", "refunded", "charged_back" -> new GatewayResult(Outcome.APPROVED, order.id());
            case "failed", "canceled" -> new GatewayResult(Outcome.DECLINED, order.id());
            // "created", "processing", "action_required": todavia no hay resultado. Se trata como un intento sin
            // respuesta: no se confirma ni se rechaza, y despues se vuelve a consultar.
            default -> {
                log.info("Mercado Pago order {} is still {} ({})", order.id(), order.status(), order.statusDetail());
                yield new GatewayResult(Outcome.NO_RESPONSE, order.id());
            }
        };
    }

    private static GatewayResult toRefundResult(Order order) {
        if (order == null) {
            return GatewayResult.of(Outcome.UNAVAILABLE);
        }
        if ("refunded".equals(order.status()) || "partially_refunded".equals(order.statusDetail())) {
            return new GatewayResult(Outcome.APPROVED, order.id());
        }
        // Sin un resultado claro la devolucion queda pendiente y se reintenta
        log.warn("Unexpected Mercado Pago refund result for order {}: {} ({})", order.id(), order.status(), order.statusDetail());
        return GatewayResult.of(Outcome.UNAVAILABLE);
    }

    // Mercado Pago recibe los montos como texto con dos decimales ("3000.00")
    private static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ---------- JSON de la API de Orders ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OrderRequest(String type, @JsonProperty("processing_mode") String processingMode,
                        @JsonProperty("total_amount") String totalAmount,
                        @JsonProperty("external_reference") String externalReference,
                        Payer payer, NewTransactions transactions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Payer(String email) {
    }

    record NewTransactions(List<NewPayment> payments) {
    }

    record NewPayment(String amount, @JsonProperty("payment_method") PaymentMethod paymentMethod) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PaymentMethod(String id, String type, String token, int installments) {
    }

    record RefundBody(List<RefundTransaction> transactions) {
    }

    record RefundTransaction(String id, String amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Order(String id, String status, @JsonProperty("status_detail") String statusDetail,
                 @JsonProperty("total_amount") String totalAmount, Transactions transactions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transactions(List<Transaction> payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transaction(String id, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderSearch(List<Order> data) {
    }

    // Respuesta de error de un cobro rechazado: la order viene en "data"
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderError(Order data) {
    }
}
