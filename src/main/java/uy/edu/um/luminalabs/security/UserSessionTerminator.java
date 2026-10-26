package uy.edu.um.luminalabs.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

// Cierra las sesiones abiertas de un usuario (al bloquear su cuenta). Spring Security guarda en el SessionRegistry
// las sesiones de cada usuario logueado (ver SecurityConfig); una sesion marcada como vencida se cierra en el
// proximo pedido y la persona vuelve al login con el aviso de cuenta bloqueada.
@Component
@RequiredArgsConstructor
public class UserSessionTerminator {

    private final SessionRegistry sessionRegistry;

    public void terminateSessionsOf(Long userId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof SecurityUser user && user.getId().equals(userId))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
