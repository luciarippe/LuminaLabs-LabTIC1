package uy.edu.um.luminalabs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
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
                                "/login", "/register", "/register/**", "/error",
                                "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/provider/**").hasRole("PROVIDER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new RoleBasedSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedPage("/access-denied"));
        return http.build();
    }

    // CU-14 3a: error generico si fallan las credenciales; 4a: se informa el estado de la solicitud
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String target = "/login?error";
            if (exception instanceof ProviderNotApprovedException notApproved) {
                String status = notApproved.getStatus() == null
                        ? "none"
                        : notApproved.getStatus().name().toLowerCase(Locale.ROOT);
                target = "/login?status=" + status;
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }
}
