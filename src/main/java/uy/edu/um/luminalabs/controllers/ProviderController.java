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
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.ProviderActivityService;
import uy.edu.um.luminalabs.services.ProviderSlotService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Espacio de gestion del prestador: el panel con el resumen. Los emprendimientos y las actividades tienen
// sus propias paginas (ProviderBusinessController, ProviderActivityController).
@Controller
@RequestMapping("/provider")
@RequiredArgsConstructor
public class ProviderController {

    private static final int UPCOMING_SLOTS_SHOWN = 6;

    private final BusinessService businessService;
    private final ProviderActivityService activityService;
    private final ProviderSlotService slotService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        Long providerId = currentUser.getId();
        List<Business> businesses = businessService.findByProvider(providerId);
        List<Activity> activities = activityService.findActivities(providerId);
        Map<Long, Long> activityCountByBusiness = activities.stream()
                .collect(Collectors.groupingBy(activity -> activity.getBusiness().getId(), Collectors.counting()));

        model.addAttribute("businesses", businesses);
        model.addAttribute("activityCountByBusiness", activityCountByBusiness);
        model.addAttribute("approvedCount", businesses.stream().filter(Business::isApproved).count());
        model.addAttribute("pendingCount", businesses.stream().filter(b -> b.getStatus() == BusinessStatus.PENDING).count());
        model.addAttribute("activityCount", activities.size());
        model.addAttribute("upcomingSlots", slotService.findUpcomingSlots(providerId, UPCOMING_SLOTS_SHOWN));
        model.addAttribute("upcomingTotals", slotService.upcomingTotals(providerId));
        model.addAttribute("runningOut", slotService.findRunningOut(activities));
        model.addAttribute("runningOutDays", ProviderSlotService.RUNNING_OUT_DAYS);
        return "provider/dashboard";
    }
}
