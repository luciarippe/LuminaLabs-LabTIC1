package uy.edu.um.luminalabs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import uy.edu.um.luminalabs.security.AccountBlockedException;
import uy.edu.um.luminalabs.security.AppUserDetailsService;
import uy.edu.um.luminalabs.security.ProviderNotApprovedException;
import uy.edu.um.luminalabs.security.RoleBasedSuccessHandler;
import uy.edu.um.luminalabs.security.SecurityUser;

import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // RNF-10: contrasenas cifradas con BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // El estado del prestador se revisa DESPUES de validar la contrasena,
        // asi no se revela el estado de una cuenta a quien no conoce la contrasena.
        provider.setPostAuthenticationChecks(user -> {
            SecurityUser securityUser = (SecurityUser) user;
            if (securityUser.isBlocked()) {
                throw new AccountBlockedException();
            }
            if (!securityUser.isLoginAllowed()) {
                throw new ProviderNotApprovedException(securityUser.getBlockingStatus());
            }
        });
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/activities", "/activities/**",
                                "/login", "/register", "/register/**", "/invitations/**", "/error",
                                "/css/**", "/js/**", "/images/**", "/photos/**").permitAll()
                        // Pasarela de pagos simulada: es "otro sistema", la llama el servidor por HTTP
                        .requestMatchers("/mock-gateway/**").permitAll()
                        // API publica (RF-25) y su documentacion (RNF-05): sin sesion
                        .requestMatchers("/api/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/provider/**").hasRole("PROVIDER")
                        .requestMatchers("/reservations/**", "/account/**").hasRole("TOURIST")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new RoleBasedSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedPage("/access-denied"))
                // El token CSRF protege los formularios del navegador. La pasarela simulada es una API que
                // llama otro programa (no un navegador con sesion), asi que no lo lleva.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mock-gateway/**"))
                // CORS de la API publica (@CrossOrigin en PublicApiController): que otros sitios la puedan llamar
                .cors(Customizer.withDefaults())
                // Registro de las sesiones de cada usuario (sin limite de sesiones), para poder cerrarlas al
                // bloquear una cuenta (UserSessionTerminator). Una sesion cerrada asi vuelve al login con el aviso.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?blocked"));
        return http.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Avisa al SessionRegistry cuando una sesion se termina (si no, quedarian sesiones viejas registradas)
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // CU-14 3a: error generico si fallan las credenciales; 4a: se informa el estado de la solicitud
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String target = "/login?error";
            if (exception instanceof AccountBlockedException) {
                target = "/login?blocked";
            } else if (exception instanceof ProviderNotApprovedException notApproved) {
                String status = notApproved.getStatus() == null
                        ? "none"
                        : notApproved.getStatus().name().toLowerCase(Locale.ROOT);
                target = "/login?status=" + status;
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }
}
