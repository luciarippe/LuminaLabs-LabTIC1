package uy.edu.um.luminalabs.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentCard.CardToken;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

// Igual que HttpPaymentGatewayTest: el cliente contra un servidor HTTP de verdad que responde como la API de
// Orders de Mercado Pago (los JSON son como los de su documentacion, con solo los campos que usamos).
class MercadoPagoPaymentGatewayTest {

    private static final int TIMEOUT_MILLIS = 300;

    private HttpServer server;
    private MercadoPagoPaymentGateway gateway;
    // Respuesta para cada "METODO /ruta", y el ultimo pedido recibido en cada una
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final Map<String, Received> received = new ConcurrentHashMap<>();
    private volatile long delayMillis;

    private record Reply(int status, String body) {
    }

    private record Received(String query, String authorization, String idempotencyKey, String body) {
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
        gateway = new MercadoPagoPaymentGateway("http://localhost:" + server.getAddress().getPort(),
                "TEST-token", TIMEOUT_MILLIS, clock);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        received.put(key, new Received(exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Idempotency-Key"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Reply reply = replies.getOrDefault(key, new Reply(404, null));
        byte[] bytes = reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private GatewayResult charge() {
        return gateway.charge(new ChargeRequest("PAY-1", new BigDecimal("3000"), "UYU",
                new CardToken("card-token", "master", "credit_card", 1, "comprador@test.com")));
    }

    @Test
    void approvedOrderSendsTokenReferenceAndCredentials() {
        replies.put("POST /v1/orders", new Reply(201,
                "{\"id\":\"ORD01\",\"status\":\"processed\",\"status_detail\":\"accredited\",\"total_amount\":\"3000.00\"}"));

        assertEquals(new GatewayResult(Outcome.APPROVED, "ORD01"), charge());

        Received request = received.get("POST /v1/orders");
        assertEquals("Bearer TEST-token", request.authorization());
        assertEquals("PAY-1", request.idempotencyKey());
        assertTrue(request.body().contains("\"external_reference\":\"PAY-1\""));
        assertTrue(request.body().contains("\"total_amount\":\"3000.00\""));
        assertTrue(request.body().contains("\"token\":\"card-token\""));
        assertTrue(request.body().contains("\"email\":\"comprador@test.com\""));
    }

    // Mercado Pago responde a un pago rechazado con un 4xx: los errores y la order "failed" en "data"
    // (respuesta copiada del sandbox, con el titular "OTHE")
    @Test
    void failedOrderIsDeclined() {
        replies.put("POST /v1/orders", new Reply(402, "{\"errors\":[{\"code\":\"failed\","
                + "\"message\":\"The following transactions failed\",\"details\":[\"PAY01: rejected_by_issuer\"]}],"
                + "\"data\":{\"id\":\"ORD02\",\"status\":\"failed\",\"status_detail\":\"failed\"}}"));

        assertEquals(new GatewayResult(Outcome.DECLINED, "ORD02"), charge());
    }

    // Un error de validacion (sin order en el cuerpo) tambien es un rechazo
    @Test
    void validationErrorIsDeclined() {
        replies.put("POST /v1/orders", new Reply(400, "{\"errors\":[{\"code\":\"invalid_email\"}]}"));

        assertEquals(Outcome.DECLINED, charge().outcome());
    }

    // Todavia sin resultado (ej. titular "CONT"): ni se confirma ni se rechaza, se consulta despues
    @Test
    void orderInProcessMeansNoResponse() {
        replies.put("POST /v1/orders", new Reply(201,
                "{\"id\":\"ORD03\",\"status\":\"processing\",\"status_detail\":\"in_process\"}"));

        assertEquals(Outcome.NO_RESPONSE, charge().outcome());
    }

    @Test
    void wrongCredentialsMeanUnavailable() {
        replies.put("POST /v1/orders", new Reply(401, "{\"message\":\"invalid access token\"}"));

        assertEquals(Outcome.UNAVAILABLE, charge().outcome());
    }

    @Test
    void serverErrorMeansUnavailable() {
        replies.put("POST /v1/orders", new Reply(500, null));

        assertEquals(Outcome.UNAVAILABLE, charge().outcome());
    }

    @Test
    void slowResponseMeansNoResponse() {
        replies.put("POST /v1/orders", new Reply(201, "{\"id\":\"ORD04\",\"status\":\"processed\"}"));
        delayMillis = TIMEOUT_MILLIS * 3;

        assertEquals(Outcome.NO_RESPONSE, charge().outcome());
    }

    @Test
    void queryFindsOrderByExternalReference() {
        replies.put("GET /v1/orders", new Reply(200,
                "{\"data\":[{\"id\":\"ORD05\",\"status\":\"processed\",\"status_detail\":\"accredited\"}],"
                        + "\"paging\":{\"total\":\"1\"}}"));

        assertEquals(new GatewayResult(Outcome.APPROVED, "ORD05"), gateway.findByReference("PAY-1"));

        String query = received.get("GET /v1/orders").query();
        assertTrue(query.contains("external_reference=PAY-1"));
        // Mercado Pago acepta un rango de 1 mes como maximo
        assertTrue(query.contains("begin_date=2026-09-28T12:00:00Z"));
        assertTrue(query.contains("end_date=2026-10-24T12:00:00Z"));
    }

    // Ninguna order con esa referencia: Mercado Pago nunca recibio el pedido
    @Test
    void queryWithoutResultsIsNotFound() {
        replies.put("GET /v1/orders", new Reply(200, "{\"data\":[],\"paging\":{\"total\":\"0\"}}"));

        assertEquals(Outcome.NOT_FOUND, gateway.findByReference("PAY-9").outcome());
    }

    @Test
    void queryThatFailsIsUnavailable() {
        replies.put("GET /v1/orders", new Reply(500, null));

        assertEquals(Outcome.UNAVAILABLE, gateway.findByReference("PAY-1").outcome());
    }

    private void orderToRefund() {
        replies.put("GET /v1/orders/ORD01", new Reply(200, "{\"id\":\"ORD01\",\"status\":\"processed\","
                + "\"total_amount\":\"3000.00\",\"transactions\":{\"payments\":[{\"id\":\"PAY01\",\"status\":\"processed\"}]}}"));
    }

    // Devolucion total: el cuerpo va vacio
    @Test
    void totalRefund() {
        orderToRefund();
        replies.put("POST /v1/orders/ORD01/refund", new Reply(201,
                "{\"id\":\"ORD01\",\"status\":\"refunded\",\"status_detail\":\"refunded\"}"));

        GatewayResult result = gateway.refund(new RefundRequest("REF-1", "ORD01", new BigDecimal("3000.00"), "UYU"));

        assertEquals(new GatewayResult(Outcome.APPROVED, "ORD01"), result);
        Received request = received.get("POST /v1/orders/ORD01/refund");
        assertEquals("REF-1", request.idempotencyKey());
        assertEquals("", request.body());
    }

    // Devolucion parcial (cancelacion con recargo): se indica la transaccion y el monto
    @Test
    void partialRefund() {
        orderToRefund();
        replies.put("POST /v1/orders/ORD01/refund", new Reply(201,
                "{\"id\":\"ORD01\",\"status\":\"processed\",\"status_detail\":\"partially_refunded\"}"));

        GatewayResult result = gateway.refund(new RefundRequest("REF-2", "ORD01", new BigDecimal("2400"), "UYU"));

        assertEquals(Outcome.APPROVED, result.outcome());
        String body = received.get("POST /v1/orders/ORD01/refund").body();
        assertTrue(body.contains("\"id\":\"PAY01\""));
        assertTrue(body.contains("\"amount\":\"2400.00\""));
    }

    // Si falla, la devolucion queda pendiente y RefundService la reintenta
    @Test
    void refundThatFailsIsUnavailable() {
        orderToRefund();
        replies.put("POST /v1/orders/ORD01/refund", new Reply(503, null));

        GatewayResult result = gateway.refund(new RefundRequest("REF-3", "ORD01", new BigDecimal("3000.00"), "UYU"));

        assertEquals(Outcome.UNAVAILABLE, result.outcome());
    }

    // Lo que respondio el sandbox al pedir una devolucion enseguida despues de otra: hay que reintentar
    @Test
    void refundWhileAnotherOperationIsPendingIsUnavailable() {
        orderToRefund();
        replies.put("POST /v1/orders/ORD01/refund", new Reply(409, "{\"errors\":[{\"code\":\"post_processing_operation_pending\","
                + "\"message\":\"The refund could not be completed because the payment is still being processed. Try again shortly.\"}]}"));

        GatewayResult result = gateway.refund(new RefundRequest("REF-4", "ORD01", new BigDecimal("3000.00"), "UYU"));

        assertEquals(Outcome.UNAVAILABLE, result.outcome());
    }
}
