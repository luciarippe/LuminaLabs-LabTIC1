package uy.edu.um.luminalabs.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
}
