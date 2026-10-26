package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.ProviderActivityService;
import uy.edu.um.luminalabs.services.ProviderReservationService;
import uy.edu.um.luminalabs.services.ProviderReservationService.Filters;
import uy.edu.um.luminalabs.services.ProviderReservationService.StatusFilter;

import java.time.LocalDate;

// CU-09 (RF-22): reservas recibidas por el prestador. Una tarjeta por horario, con filtros; el detalle de cada
// reserva y la lista de participantes de un horario para organizar la salida.
@Controller
@RequestMapping("/provider/reservations")
@RequiredArgsConstructor
public class ProviderReservationController {

    private final ProviderReservationService reservationService;
    private final BusinessService businessService;
    private final ProviderActivityService activityService;

    // Pasos 1 a 4. Los filtros van en la URL (se pueden guardar o compartir).
    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUser currentUser,
                       @RequestParam(required = false) Long businessId,
                       @RequestParam(required = false) Long activityId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       @RequestParam(required = false) StatusFilter status, Model model) {
        Long providerId = currentUser.getId();
        Filters filters = new Filters(businessId, activityId, dateFrom, dateTo, status);
        var slots = reservationService.find(providerId, filters);
        model.addAttribute("slots", slots);
        model.addAttribute("filters", filters);
        model.addAttribute("statusFilters", StatusFilter.values());
        model.addAttribute("businesses", businessService.findByProvider(providerId));
        model.addAttribute("activities", activityService.findActivities(providerId));
        // 2a: distinguir "todavia no hay reservas" de "ninguna coincide con los filtros" (4a)
        model.addAttribute("hasAnyReservation", !slots.isEmpty() || reservationService.hasAnyReservation(providerId));
        return "provider/reservations";
    }

    // Pasos 5 y 6
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        model.addAttribute("detail", reservationService.findReservation(currentUser.getId(), id));
        return "provider/reservation-detail";
    }

    // 6a
    @GetMapping("/slots/{slotId}/participants")
    public String participants(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long slotId, Model model) {
        model.addAttribute("list", reservationService.findSlotParticipants(currentUser.getId(), slotId));
        return "provider/slot-participants";
    }
}
