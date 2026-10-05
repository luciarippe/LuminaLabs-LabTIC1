package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.TouristRegistrationForm;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.services.TouristRegistrationService;

// CU-12: registrarse como turista
@Controller
@RequestMapping("/register")
@RequiredArgsConstructor
public class TouristRegistrationController {

    private final TouristRegistrationService registrationService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("form", new TouristRegistrationForm());
        return "auth/register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") TouristRegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                registrationService.register(form);
                redirectAttributes.addFlashAttribute("successMessage",
                        "¡Tu cuenta fue creada! Ya podés iniciar sesión.");
                return "redirect:/login";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        // No se devuelve la contrasena al formulario
        form.setPassword(null);
        form.setConfirmPassword(null);
        return "auth/register";
    }
}
