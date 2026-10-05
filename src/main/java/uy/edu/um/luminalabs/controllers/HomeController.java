package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.luminalabs.dto.ActivitySearchCriteria;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.services.ActivityCatalogService;

// Portada: el catalogo de actividades, visible sin iniciar sesion (RF-12)
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int FEATURED_LIMIT = 8;

    private final ActivityCatalogService catalogService;

    @GetMapping("/")
    public String home(Model model) {
        ActivitySearchCriteria criteria = new ActivitySearchCriteria();
        criteria.setSort(ActivitySearchCriteria.Sort.DATE);
        model.addAttribute("featured", catalogService.search(criteria).stream().limit(FEATURED_LIMIT).toList());
        model.addAttribute("categories", catalogService.findCategories());
        model.addAttribute("destinations", catalogService.findDestinations());
        model.addAttribute("departments", Department.values());
        return "index";
    }
}
