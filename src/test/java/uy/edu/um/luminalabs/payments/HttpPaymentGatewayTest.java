package uy.edu.um.luminalabs.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Prueba el cliente HTTP contra un servidor HTTP de verdad (el que trae el JDK), simulando cada respuesta posible
// de la pasarela. Los tests con mocks de PaymentGateway no pueden detectar problemas de la conexion en si.
class HttpPaymentGatewayTest {

    private static final int TIMEOUT_MILLIS = 300;

    private HttpServer server;
    private HttpPaymentGateway gateway;
    // Lo que va a responder el servidor en cada test
    private volatile int status;
    private volatile String body;
    private volatile long delayMillis;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments", this::respond);
        server.start();
        gateway = new HttpPaymentGateway("http://localhost:" + server.getAddress().getPort(), TIMEOUT_MILLIS);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private void serverReplies(int status, String body) {
        this.status = status;
        this.body = body;
    }

    private GatewayResult charge() {
        return gateway.charge(new ChargeRequest("PAY-1", BigDecimal.TEN, "UYU", "4111111111111111", "Ana", "12/30", "123"));
    }

    @Test
    void approved() {
        serverReplies(201, "{\"id\":\"gw-1\",\"reference\":\"PAY-1\",\"status\":\"APPROVED\"}");

        assertEquals(new GatewayResult(Outcome.APPROVED, "gw-1"), charge());
    }

    @Test
    void declined() {
        serverReplies(201, "{\"id\":\"gw-2\",\"reference\":\"PAY-1\",\"status\":\"DECLINED\"}");

        assertEquals(Outcome.DECLINED, charge().outcome());
    }

    @Test
    void serverErrorMeansUnavailable() {
        serverReplies(500, null);

        assertEquals(Outcome.UNAVAILABLE, charge().outcome());
    }

    // El caso que se nos escapo la primera vez: el timeout llegaba como SocketTimeoutException sin envolver
    @Test
    void slowResponseMeansNoResponse() {
        serverReplies(201, "{\"id\":\"gw-3\",\"reference\":\"PAY-1\",\"status\":\"APPROVED\"}");
        delayMillis = TIMEOUT_MILLIS * 3;

        assertEquals(Outcome.NO_RESPONSE, charge().outcome());
    }

    @Test
    void unreachableServerMeansUnavailable() {
        server.stop(0);

        assertEquals(Outcome.UNAVAILABLE, charge().outcome());
    }

    @Test
    void queryOfUnknownReferenceIsNotFound() {
        serverReplies(404, null);

        assertEquals(Outcome.NOT_FOUND, gateway.findByReference("PAY-9").outcome());
    }

    @Test
    void queryThatTimesOutIsUnavailable() {
        serverReplies(200, "{\"id\":\"gw-3\",\"reference\":\"PAY-1\",\"status\":\"APPROVED\"}");
        delayMillis = TIMEOUT_MILLIS * 3;

        assertEquals(Outcome.UNAVAILABLE, gateway.findByReference("PAY-1").outcome());
    }
}
