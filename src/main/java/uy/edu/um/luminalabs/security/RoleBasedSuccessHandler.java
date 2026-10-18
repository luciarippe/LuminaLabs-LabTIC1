package uy.edu.um.luminalabs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

// Despues de iniciar sesion, cada rol va a su seccion (CU-14, paso 5)
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    // Las sesiones de administrador expiran antes por seguridad
    static final int ADMIN_SESSION_TIMEOUT_SECONDS = 15 * 60;

    // Cuando un visitante entra a una pagina protegida (ej. reservar), Spring Security guarda esa pagina
    // en la sesion antes de mandarlo al login. Aca se la recupera.
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);

        String target = switch (user.getRole()) {
            case ADMIN -> {
                request.getSession().setMaxInactiveInterval(ADMIN_SESSION_TIMEOUT_SECONDS);
                yield "/admin";
            }
            case PROVIDER -> "/provider";
            case TOURIST -> null;
        };
        if (target == null) {
            // CU-07 7b y CU-12 1a: el turista que quiso reservar sin sesion vuelve a esa reserva. Solo se vuelve a
            // paginas de reservas: Spring tambien guarda pedidos automaticos del navegador (ej. /.well-known/...).
            boolean wasBooking = savedRequest != null
                    && savedRequest.getRedirectUrl().contains(request.getContextPath() + "/reservations");
            response.sendRedirect(wasBooking ? savedRequest.getRedirectUrl() : request.getContextPath() + "/");
            return;
        }
        response.sendRedirect(request.getContextPath() + target);
    }
}
