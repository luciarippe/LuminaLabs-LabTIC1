package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.services.RatingModerationService;

// Administracion > Valoraciones: moderar las resenas (ocultarlas o volver a mostrarlas)
@Controller
@RequestMapping("/admin/ratings")
@RequiredArgsConstructor
public class AdminRatingController {

    private final RatingModerationService moderationService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "ALL") RatingModerationService.Filter filter,
                       @RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("search", moderationService.search(filter, q));
        model.addAttribute("filter", filter);
        model.addAttribute("query", q.trim());
        return "admin/ratings";
    }

    // Se vuelve a la misma pestana y busqueda, con el ancla en la valoracion
    @PostMapping("/{id}/hide")
    public String hide(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                       @RequestParam(defaultValue = "ALL") String filter, @RequestParam(defaultValue = "") String q,
                       RedirectAttributes redirectAttributes) {
        try {
            moderationService.hide(id, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Ocultamos la valoración: ya no se muestra ni cuenta en el promedio. Le avisamos por correo a quien la escribió.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return back(id, filter, q, redirectAttributes);
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, @RequestParam(defaultValue = "ALL") String filter,
                          @RequestParam(defaultValue = "") String q, RedirectAttributes redirectAttributes) {
        try {
            moderationService.restore(id);
            redirectAttributes.addFlashAttribute("successMessage", "La valoración se volvió a mostrar en la actividad.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return back(id, filter, q, redirectAttributes);
    }

    private static String back(Long id, String filter, String q, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("filter", filter);
        redirectAttributes.addAttribute("q", q);
        return "redirect:/admin/ratings#rating-" + id;
    }
}
