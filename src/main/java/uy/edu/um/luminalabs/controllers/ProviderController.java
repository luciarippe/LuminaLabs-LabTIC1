package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.ProviderActivityService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Espacio de gestion del prestador: sus emprendimientos y las actividades de cada uno
@Controller
@RequestMapping("/provider")
@RequiredArgsConstructor
public class ProviderController {

    private final BusinessService businessService;
    private final ProviderActivityService activityService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        Map<Long, List<Activity>> activitiesByBusiness = activityService.findActivities(currentUser.getId()).stream()
                .collect(Collectors.groupingBy(activity -> activity.getBusiness().getId()));
        model.addAttribute("businesses", businessService.findByProvider(currentUser.getId()));
        model.addAttribute("activitiesByBusiness", activitiesByBusiness);
        return "provider/dashboard";
    }
}
