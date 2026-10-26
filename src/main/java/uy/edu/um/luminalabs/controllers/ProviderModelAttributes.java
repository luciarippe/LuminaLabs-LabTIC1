package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.ProviderSlotService;

// Datos que necesita el menu lateral en todas las paginas del espacio del prestador
@ControllerAdvice(assignableTypes = {ProviderController.class, ProviderActivityController.class,
        ProviderBusinessController.class, ProviderAccountController.class, ProviderReservationController.class,
        ProviderTeamController.class})
@RequiredArgsConstructor
public class ProviderModelAttributes {

    private final ProviderSlotService slotService;

    // Cuantas actividades tienen los horarios por terminarse (numero junto a "Mis actividades")
    @ModelAttribute("runningOutCount")
    public int runningOutCount(@AuthenticationPrincipal SecurityUser currentUser) {
        return currentUser == null ? 0 : slotService.findRunningOut(currentUser.getId()).size();
    }
}
