package uy.edu.um.luminalabs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// Reloj de la aplicacion en hora de Uruguay. Inyectarlo (en vez de usar LocalDateTime.now())
// permite fijar la hora en los tests.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Montevideo"));
    }
}
