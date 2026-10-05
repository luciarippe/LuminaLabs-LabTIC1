package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.dto.ActivitySearchCriteria;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.services.ActivityCatalogService;
import uy.edu.um.luminalabs.services.LocationService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// CU-07: explorar y buscar actividades. Publico, no requiere sesion.
@Controller
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityCatalogService catalogService;
    private final LocationService locationService;
    private final Clock clock;

    @GetMapping
    public String search(@ModelAttribute("criteria") ActivitySearchCriteria criteria, Model model) {
        model.addAttribute("results", catalogService.search(criteria));
        model.addAttribute("categories", catalogService.findCategories());
        model.addAttribute("departments", Department.values());
        model.addAttribute("locationsByDepartment", locationService.findAllGroupedByDepartment());
        return "activities/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Activity activity = catalogService.findPublicActivity(id);
        List<Slot> slots = catalogService.findUpcomingSlots(id);
        model.addAttribute("activity", activity);
        model.addAttribute("slots", slots);
        model.addAttribute("fromPrice", slots.stream().map(Slot::getPrice).min(Comparator.naturalOrder()).orElse(null));
        model.addAttribute("now", LocalDateTime.now(clock));
        return "activities/detail";
    }
}
