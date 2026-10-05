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
import uy.edu.um.luminalabs.dto.ProviderRegistrationForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.services.LocationService;
import uy.edu.um.luminalabs.services.ProviderRegistrationService;

import java.util.List;
import java.util.Map;

// CU-02: registrar un emprendimiento
@Controller
@RequestMapping("/register/provider")
@RequiredArgsConstructor
public class ProviderRegistrationController {

    private final ProviderRegistrationService registrationService;
    private final LocationService locationService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("form", new ProviderRegistrationForm());
        return "provider/register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") ProviderRegistrationForm form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Business business = registrationService.register(form);
                redirectAttributes.addFlashAttribute("businessName", business.getLegalName());
                redirectAttributes.addFlashAttribute("email", form.getEmail());
                return "redirect:/register/provider/success";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        // No se devuelve la contrasena al formulario
        form.setPassword(null);
        form.setConfirmPassword(null);
        return "provider/register";
    }

    @GetMapping("/success")
    public String success(Model model) {
        if (!model.containsAttribute("businessName")) {
            return "redirect:/register/provider";
        }
        return "provider/register-success";
    }

    @ModelAttribute("businessTypes")
    public BusinessType[] businessTypes() {
        return BusinessType.values();
    }

    @ModelAttribute("locationsByDepartment")
    public Map<Department, List<Location>> locationsByDepartment() {
        return locationService.findAllGroupedByDepartment();
    }
}
