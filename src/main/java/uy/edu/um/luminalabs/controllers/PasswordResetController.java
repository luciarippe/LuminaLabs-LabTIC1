package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.PasswordResetForm;
import uy.edu.um.luminalabs.dto.PasswordResetRequestForm;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.services.PasswordResetService;

// Recuperar la contrasena olvidada (ver PasswordResetService). Es publico: la persona no puede iniciar sesion;
// lo que protege el paso 2 es el token del enlace del correo (ver SecurityConfig).
@Controller
@RequestMapping("/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot")
    public String requestForm(Model model) {
        model.addAttribute("form", new PasswordResetRequestForm());
        return "auth/forgot-password";
    }

    // Siempre la misma respuesta, exista o no una cuenta con ese correo
    @PostMapping("/forgot")
    public String request(@Valid @ModelAttribute("form") PasswordResetRequestForm form, BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }
        passwordResetService.requestReset(form.getEmail());
        model.addAttribute("sentTo", form.getEmail().trim());
        return "auth/forgot-password";
    }

    @GetMapping("/reset/{token}")
    public String resetForm(@PathVariable String token, Model model) {
        try {
            User user = passwordResetService.findUsableToken(token).getUser();
            model.addAttribute("form", new PasswordResetForm());
            return showResetForm(token, user, model);
        } catch (IllegalStateException e) {
            model.addAttribute("invalidMessage", e.getMessage());
            return "auth/reset-password";
        }
    }

    @PostMapping("/reset/{token}")
    public String reset(@PathVariable String token, @Valid @ModelAttribute("form") PasswordResetForm form,
                        BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        User user;
        try {
            user = passwordResetService.findUsableToken(token).getUser();
        } catch (IllegalStateException e) {
            model.addAttribute("invalidMessage", e.getMessage());
            return "auth/reset-password";
        }
        if (!bindingResult.hasErrors()) {
            try {
                passwordResetService.resetPassword(token, form);
                redirectAttributes.addFlashAttribute("successMessage", "¡Listo, " + user.getFirstName()
                        + "! Cambiamos tu contraseña. Ya podés iniciar sesión con la nueva.");
                return "redirect:/login";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        return showResetForm(token, user, model);
    }

    private String showResetForm(String token, User user, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("username", user.getUsername());
        return "auth/reset-password";
    }
}
