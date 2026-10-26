package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.AddProviderForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.ProviderTeamService;

// CU-15: los prestadores de un emprendimiento. Esta bajo /provider (solo ROLE_PROVIDER) y ProviderTeamService
// verifica que el emprendimiento sea del prestador.
@Controller
@RequestMapping("/provider/businesses/{id}/providers")
@RequiredArgsConstructor
public class ProviderTeamController {

    private final ProviderTeamService teamService;

    // Paso 2
    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        model.addAttribute("form", new AddProviderForm());
        return showPage(currentUser, id, model);
    }

    // Pasos 3 a 5, 4a a 4c
    @PostMapping
    public String add(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                      @Valid @ModelAttribute("form") AddProviderForm form, BindingResult bindingResult,
                      Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ProviderTeamService.AddResult result = teamService.add(currentUser.getId(), id, form.getEmail());
                redirectAttributes.addFlashAttribute("successMessage", switch (result.outcome()) {
                    case ADDED -> result.name() + " ya es prestador del emprendimiento. Le avisamos por correo.";
                    case INVITED -> "No había una cuenta con " + result.email() + ": le mandamos una invitación para "
                            + "que cree su cuenta de prestador. Cuando la acepte, va a aparecer en la lista.";
                    case INVITATION_RESENT -> "Le volvimos a mandar la invitación a " + result.email()
                            + ". El enlace anterior ya no sirve.";
                });
                return "redirect:/provider/businesses/" + id + "/providers";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/provider/businesses/" + id + "/providers";
            }
        }
        return showPage(currentUser, id, model);
    }

    // 3a y 3b
    @PostMapping("/{providerId}/remove")
    public String remove(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                         @PathVariable Long providerId, RedirectAttributes redirectAttributes) {
        try {
            Provider removed = teamService.remove(currentUser.getId(), id, providerId);
            redirectAttributes.addFlashAttribute("successMessage", "Quitamos a " + removed.getFullName()
                    + " de los prestadores. Le avisamos por correo.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/provider/businesses/" + id + "/providers";
    }

    @PostMapping("/invitations/{invitationId}/cancel")
    public String cancelInvitation(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                                   @PathVariable Long invitationId, RedirectAttributes redirectAttributes) {
        try {
            teamService.cancelInvitation(currentUser.getId(), id, invitationId);
            redirectAttributes.addFlashAttribute("successMessage", "Cancelamos la invitación: el enlace ya no sirve.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/provider/businesses/" + id + "/providers";
    }

    private String showPage(SecurityUser currentUser, Long id, Model model) {
        Business business = teamService.findManagedBusiness(currentUser.getId(), id);
        model.addAttribute("business", business);
        model.addAttribute("providers", teamService.providersOf(business));
        model.addAttribute("invitations", teamService.pendingInvitations(id));
        return "provider/business-providers";
    }
}
