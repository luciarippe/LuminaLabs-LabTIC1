package uy.edu.um.luminalabs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RNF-05: datos generales de la documentacion de la API publica. springdoc arma el resto a partir de las
// anotaciones de PublicApiController. Pagina: /api/docs; especificacion OpenAPI: /api/openapi.json.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI publicApi() {
        return new OpenAPI().info(new Info()
                .title("API de Uruguay Mnatural")
                .version("v1")
                .description("API pública y de solo lectura para integrar otras plataformas con el catálogo de "
                        + "experiencias turísticas de Uruguay Mnatural (RF-25). No requiere autenticación y responde "
                        + "en JSON. Solo incluye emprendimientos aprobados y sus actividades. Los errores de parámetros "
                        + "se responden con estado 400 y un mensaje que describe el problema.")
                .contact(new Contact().name("Lúmina Labs").email("soporte@uruguaymnatural.uy")));
    }
}
