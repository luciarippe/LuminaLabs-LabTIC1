package uy.edu.um.luminalabs.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.luminalabs.dto.ReservationForm;
import uy.edu.um.luminalabs.security.SecurityUser;

// Datos disponibles en todas las vistas
@ControllerAdvice
public class GlobalModelAttributes {

    // El usuario logueado (null si es un visitante)
    @ModelAttribute("currentUser")
    public SecurityUser currentUser(@AuthenticationPrincipal SecurityUser user) {
        return user;
    }

    // Maximo de personas por reserva, para los campos "Personas". Va como variable porque Thymeleaf no deja
    // acceder a clases ni a beans desde los atributos th:data-*.
    @ModelAttribute("maxParticipantsPerReservation")
    public int maxParticipantsPerReservation() {
        return ReservationForm.MAX_PARTICIPANTS;
    }
}
