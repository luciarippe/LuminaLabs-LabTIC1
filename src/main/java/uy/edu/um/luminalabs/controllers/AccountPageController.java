package uy.edu.um.luminalabs.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.PasswordChangeForm;
import uy.edu.um.luminalabs.dto.ProfileForm;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.security.SessionUserRefresher;
import uy.edu.um.luminalabs.services.AccountService;

// "Mi cuenta" del sitio publico (turistas y prestadores): sus datos y su contrasena, en dos formularios de la
// misma pagina. Cada subclase define su ruta con @RequestMapping, su plantilla y lo que cambia en la pagina.
// El administrador tiene la suya dentro del panel (AdminController).
public abstract class AccountPageController {

    private final AccountService accountService;
    private final SessionUserRefresher sessionUserRefresher;

    protected AccountPageController(AccountService accountService, SessionUserRefresher sessionUserRefresher) {
        this.accountService = accountService;
        this.sessionUserRefresher = sessionUserRefresher;
    }

    // Ruta de la pagina, ej. "/account"; los formularios se envian a <ruta>/profile y <ruta>/password
    protected abstract String basePath();

    // Plantilla de la pagina (usa los fragmentos de fragments/account)
    protected abstract String viewName();

    // Ayuda debajo del correo: para que se usa en este rol
    protected abstract String emailHint();

    @GetMapping
    public String account(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        model.addAttribute("profileForm", accountService.profileOf(currentUser.getId()));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return accountPage(currentUser, model);
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal SecurityUser currentUser,
                                @Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult bindingResult,
                                Model model, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        if (!bindingResult.hasErrors()) {
            try {
                User user = accountService.updateProfile(currentUser.getId(), form);
                sessionUserRefresher.refresh(user, request, response);
                redirectAttributes.addFlashAttribute("successMessage", "Tus datos se guardaron.");
                return "redirect:" + basePath();
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return accountPage(currentUser, model);
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal SecurityUser currentUser,
                                 @Valid @ModelAttribute("passwordForm") PasswordChangeForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                accountService.changePassword(currentUser.getId(), form);
                redirectAttributes.addFlashAttribute("successMessage", "Tu contraseña se cambió.");
                return "redirect:" + basePath();
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        // Las contrasenas no se devuelven al formulario
        form.setCurrentPassword(null);
        form.setNewPassword(null);
        form.setConfirmPassword(null);
        model.addAttribute("profileForm", accountService.profileOf(currentUser.getId()));
        return accountPage(currentUser, model);
    }

    private String accountPage(SecurityUser currentUser, Model model) {
        model.addAttribute("user", accountService.findUser(currentUser.getId()));
        model.addAttribute("accountPath", basePath());
        model.addAttribute("emailHint", emailHint());
        return viewName();
    }
}
