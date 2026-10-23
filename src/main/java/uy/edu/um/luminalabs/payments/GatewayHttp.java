package uy.edu.um.luminalabs.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;

import java.net.SocketTimeoutException;
import java.time.Duration;

// Lo que comparten los clientes HTTP de las pasarelas (la simulada y Mercado Pago)
@Slf4j
final class GatewayHttp {

    private GatewayHttp() {
    }

    // Sin timeout, si la pasarela no responde el turista quedaria esperando para siempre
    static SimpleClientHttpRequestFactory requestFactory(long timeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        return requestFactory;
    }

    // Un timeout NO significa que no se cobro: el pedido pudo llegar y procesarse sin que nos llegue la
    // respuesta. Por eso queda "sin respuesta" y despues se consulta. Si no se pudo ni conectar, no se cobro.
    static GatewayResult connectionProblem(String reference, Exception e) {
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
}
