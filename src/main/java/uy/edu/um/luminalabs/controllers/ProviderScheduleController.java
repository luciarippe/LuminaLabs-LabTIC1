package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.ProviderSlotService;
import uy.edu.um.luminalabs.services.ProviderSlotService.ActivitySchedule;

import java.util.List;

// Horarios del prestador: todos los horarios abiertos, agrupados por actividad, con los lugares y las reservas
// que tiene cada uno hasta el momento. Para cargar o cambiar horarios se entra a la actividad (CU-04).
@Controller
@RequestMapping("/provider/schedule")
@RequiredArgsConstructor
public class ProviderScheduleController {

    private final ProviderSlotService slotService;
    private final BusinessService businessService;

    @GetMapping
    public String schedule(@AuthenticationPrincipal SecurityUser currentUser,
                           @RequestParam(required = false) Long businessId, Model model) {
        Long providerId = currentUser.getId();
        List<Business> businesses = businessService.findByProvider(providerId).stream()
                .filter(Business::isApproved)
                .toList();
        List<ActivitySchedule> schedule = slotService.findSchedule(providerId, businessId);
        model.addAttribute("schedule", schedule);
        model.addAttribute("businesses", businesses);
        model.addAttribute("businessId", businessId);
        model.addAttribute("slotCount", schedule.stream().mapToInt(item -> item.slots().size()).sum());
        model.addAttribute("reservedSeats", schedule.stream().mapToInt(ActivitySchedule::reservedSeats).sum());
        model.addAttribute("capacity", schedule.stream().mapToInt(ActivitySchedule::capacity).sum());
        model.addAttribute("reservations", schedule.stream().mapToLong(ActivitySchedule::reservations).sum());
        return "provider/schedule";
    }
}
