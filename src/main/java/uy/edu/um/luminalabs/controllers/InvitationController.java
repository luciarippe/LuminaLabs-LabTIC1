package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.InvitationAcceptForm;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.ProviderInvitation;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.services.ProviderTeamService;

// CU-15 4a: la persona invitada entra con el enlace del correo, completa sus datos y elige su contrasena.
// Es publico (no tiene cuenta todavia); lo que protege es el token del enlace (ver SecurityConfig).
@Controller
@RequestMapping("/invitations/{token}")
@RequiredArgsConstructor
public class InvitationController {

    private final ProviderTeamService teamService;

    @GetMapping
    public String form(@PathVariable String token, Model model) {
        try {
            ProviderInvitation invitation = teamService.findPendingInvitation(token);
            InvitationAcceptForm form = new InvitationAcceptForm();
            form.setEmail(invitation.getEmail());
            model.addAttribute("form", form);
            return showForm(token, invitation, model);
        } catch (IllegalStateException e) {
            model.addAttribute("invalidMessage", e.getMessage());
            return "auth/invitation";
        }
    }

    @PostMapping
    public String accept(@PathVariable String token, @Valid @ModelAttribute("form") InvitationAcceptForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        ProviderInvitation invitation;
        try {
            invitation = teamService.findPendingInvitation(token);
        } catch (IllegalStateException e) {
            model.addAttribute("invalidMessage", e.getMessage());
            return "auth/invitation";
        }
        // El correo siempre es el de la invitacion, aunque se haya cambiado en el formulario
        form.setEmail(invitation.getEmail());
        if (!bindingResult.hasErrors()) {
            try {
                Provider provider = teamService.accept(token, form);
                redirectAttributes.addFlashAttribute("successMessage", "¡Listo, " + provider.getFirstName()
                        + "! Ya tenés tu cuenta de prestador de " + invitation.getBusiness().getLegalName()
                        + ". Iniciá sesión con tu usuario o tu correo.");
                return "redirect:/login";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        return showForm(token, invitation, model);
    }

    private String showForm(String token, ProviderInvitation invitation, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("invitation", invitation);
        return "auth/invitation";
    }
}
