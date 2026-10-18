package uy.edu.um.luminalabs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync: correos y devoluciones en segundo plano, sin hacer esperar al usuario.
// @EnableScheduling: el proceso automatico de reservas (ReservationScheduler, CU-18).
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
