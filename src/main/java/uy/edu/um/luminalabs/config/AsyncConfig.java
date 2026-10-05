package uy.edu.um.luminalabs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Permite enviar los correos en segundo plano, sin hacer esperar al usuario
@Configuration
@EnableAsync
public class AsyncConfig {
}
