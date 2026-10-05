package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.BusinessStatusCounts;

import java.util.function.Supplier;

// Seccion de administracion (solo ROLE_ADMIN, ver SecurityConfig)
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BusinessService businessService;

    // Se usa en el menu lateral y en los indicadores de todas las paginas de administracion
    @ModelAttribute("statusCounts")
    public BusinessStatusCounts statusCounts() {
        return businessService.countByStatus();
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("pendingBusinesses", businessService.findByStatus(BusinessStatus.PENDING));
        return "admin/dashboard";
    }

    // CU-06 pasos 1-2 y CU-11 paso 1
    @GetMapping("/businesses")
    public String businesses(@RequestParam(required = false) BusinessStatus status, Model model) {
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", BusinessStatus.values());
        model.addAttribute("businesses", businessService.findByStatus(status));
        return "admin/businesses";
    }

    @GetMapping("/businesses/{id}")
    public String businessDetail(@PathVariable Long id, Model model) {
        model.addAttribute("business", businessService.findDetail(id));
        return "admin/business-detail";
    }

    @PostMapping("/businesses/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return changeStatus(id, redirectAttributes, () -> businessService.approve(id), "aprobado");
    }

    @PostMapping("/businesses/{id}/deny")
    public String deny(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                       RedirectAttributes redirectAttributes) {
        if (reason.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Indicá el motivo del rechazo.");
            return redirectToDetail(id);
        }
        return changeStatus(id, redirectAttributes, () -> businessService.deny(id, reason.trim()), "denegado");
    }

    @PostMapping("/businesses/{id}/suspend")
    public String suspend(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                          RedirectAttributes redirectAttributes) {
        if (reason.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Indicá el motivo de la suspensión.");
            return redirectToDetail(id);
        }
        return changeStatus(id, redirectAttributes, () -> businessService.suspend(id, reason.trim()), "suspendido");
    }

    @PostMapping("/businesses/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return changeStatus(id, redirectAttributes, () -> businessService.reactivate(id), "reactivado");
    }

    private String changeStatus(Long id, RedirectAttributes redirectAttributes,
                                Supplier<Business> action, String pastTenseVerb) {
        try {
            Business business = action.get();
            redirectAttributes.addFlashAttribute("successMessage",
                    "El emprendimiento \"" + business.getLegalName() + "\" fue " + pastTenseVerb + ".");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToDetail(id);
    }

    private String redirectToDetail(Long id) {
        return "redirect:/admin/businesses/" + id;
    }
}
