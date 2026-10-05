package uy.edu.um.luminalabs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

// Despues de iniciar sesion, cada rol va a su seccion (CU-14, paso 5)
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    // Las sesiones de administrador expiran antes por seguridad
    static final int ADMIN_SESSION_TIMEOUT_SECONDS = 15 * 60;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        String target = switch (user.getRole()) {
            case ADMIN -> {
                request.getSession().setMaxInactiveInterval(ADMIN_SESSION_TIMEOUT_SECONDS);
                yield "/admin";
            }
            case PROVIDER -> "/provider";
            case TOURIST -> "/";
        };
        response.sendRedirect(request.getContextPath() + target);
    }
}
