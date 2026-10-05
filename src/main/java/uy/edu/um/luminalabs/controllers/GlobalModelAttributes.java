package uy.edu.um.luminalabs.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.luminalabs.security.SecurityUser;

// Deja disponible el usuario logueado en todas las vistas (null si es un visitante)
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("currentUser")
    public SecurityUser currentUser(@AuthenticationPrincipal SecurityUser user) {
        return user;
    }
}
