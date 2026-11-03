package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.ProviderActivityService;
import uy.edu.um.luminalabs.services.ProviderReservationService;
import uy.edu.um.luminalabs.services.ProviderReservationService.Filters;
import uy.edu.um.luminalabs.services.ProviderReservationService.SlotReservations;
import uy.edu.um.luminalabs.services.ProviderSlotService;

import java.util.List;

// Espacio de gestion del prestador: el panel, solo con lo del dia a dia (lo que viene y lo ultimo que entro).
// Todo lo demas esta en su pagina del menu lateral.
@Controller
@RequestMapping("/provider")
@RequiredArgsConstructor
public class ProviderController {

    private static final int NEXT_OUTINGS_SHOWN = 5;
    private static final int RECENT_RESERVATIONS_SHOWN = 6;

    private final BusinessService businessService;
    private final ProviderActivityService activityService;
    private final ProviderSlotService slotService;
    private final ProviderReservationService reservationService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        Long providerId = currentUser.getId();
        List<Business> businesses = businessService.findByProvider(providerId);
        List<Activity> activities = activityService.findActivities(providerId);
        // Sin filtros: las reservas vigentes de los horarios que todavia no empezaron, agrupadas por horario
        List<SlotReservations> upcoming = reservationService.find(providerId, new Filters(null, null, null, null, null));

        model.addAttribute("approvedCount", businesses.stream().filter(Business::isApproved).count());
        model.addAttribute("pendingCount", businesses.stream().filter(b -> b.getStatus() == BusinessStatus.PENDING).count());
        model.addAttribute("activityCount", activities.size());
        model.addAttribute("activeReservations", upcoming.stream().mapToInt(item -> item.reservations().size()).sum());
        model.addAttribute("activePeople", upcoming.stream().flatMap(item -> item.reservations().stream())
                .mapToInt(Reservation::getParticipantCount).sum());
        model.addAttribute("nextOutings", upcoming.stream().limit(NEXT_OUTINGS_SHOWN).toList());
        model.addAttribute("upcomingTotals", slotService.upcomingTotals(providerId));
        model.addAttribute("recentReservations", reservationService.findRecent(providerId, RECENT_RESERVATIONS_SHOWN));
        model.addAttribute("runningOut", slotService.findRunningOut(activities));
        model.addAttribute("runningOutDays", ProviderSlotService.RUNNING_OUT_DAYS);
        return "provider/dashboard";
    }
}
