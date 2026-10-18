package uy.edu.um.luminalabs.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.security.SecurityUser;

// El POST de /login y /logout lo procesa Spring Security (ver SecurityConfig)
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal SecurityUser currentUser) {
        if (currentUser != null) {
            return switch (currentUser.getRole()) {
                case ADMIN -> "redirect:/admin";
                case PROVIDER -> "redirect:/provider";
                case TOURIST -> "redirect:/";
            };
        }
        return "auth/login";
    }

    // Acepta cualquier metodo: Spring Security reenvia aca el mismo pedido que fue rechazado, y si era un POST
    // (ej. un formulario con el token CSRF vencido) con @GetMapping se mostraba un error 405 en vez de esta pagina
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
}
