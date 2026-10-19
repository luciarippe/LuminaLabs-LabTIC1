package uy.edu.um.luminalabs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.entities.User;

// La sesion guarda una copia del usuario (SecurityUser). Cuando el usuario cambia sus datos en "Mi cuenta"
// se reemplaza, para que el encabezado y los menus muestren el nombre nuevo sin volver a iniciar sesion.
@Component
public class SessionUserRefresher {

    // Donde Spring Security guarda el usuario logueado entre pedidos (la sesion HTTP)
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void refresh(User user, HttpServletRequest request, HttpServletResponse response) {
        SecurityUser updated = SecurityUser.from(user);
        updated.eraseCredentials();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
