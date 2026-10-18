package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.NewBusinessForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.LocationService;
import uy.edu.um.luminalabs.services.ProviderRegistrationService;

import java.util.List;
import java.util.Map;

// CU-02 1a: un prestador logueado registra otro emprendimiento (o vuelve a postular uno denegado)
@Controller
@RequestMapping("/provider/businesses")
@RequiredArgsConstructor
public class ProviderBusinessController {

    private final ProviderRegistrationService registrationService;
    private final LocationService locationService;

    @GetMapping("/new")
    public String showForm(Model model) {
        model.addAttribute("form", new NewBusinessForm());
        return "provider/business-new";
    }

    @PostMapping("/new")
    public String register(@AuthenticationPrincipal SecurityUser currentUser,
                           @Valid @ModelAttribute("form") NewBusinessForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Business business = registrationService.registerForProvider(currentUser.getId(), form.getBusiness());
                redirectAttributes.addFlashAttribute("successMessage", "Recibimos la solicitud de \""
                        + business.getLegalName() + "\". Te avisaremos por correo cuando sea revisada.");
                return "redirect:/provider";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        return "provider/business-new";
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
