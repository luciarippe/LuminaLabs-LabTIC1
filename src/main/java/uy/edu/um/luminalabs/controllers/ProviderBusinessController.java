package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.NewBusinessForm;
import uy.edu.um.luminalabs.dto.SocialLinksForm;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Bank;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.LocationService;
import uy.edu.um.luminalabs.services.ProviderActivityService;
import uy.edu.um.luminalabs.services.ProviderRegistrationService;
import uy.edu.um.luminalabs.services.SocialLinksService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Emprendimientos del prestador: el listado con sus actividades, y CU-02 1a: registrar otro emprendimiento
// (o volver a postular uno denegado)
@Controller
@RequestMapping("/provider/businesses")
@RequiredArgsConstructor
public class ProviderBusinessController {

    private final ProviderRegistrationService registrationService;
    private final LocationService locationService;
    private final BusinessService businessService;
    private final ProviderActivityService activityService;
    private final SocialLinksService socialLinksService;

    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        Map<Long, List<Activity>> activitiesByBusiness = activityService.findActivities(currentUser.getId()).stream()
                .collect(Collectors.groupingBy(activity -> activity.getBusiness().getId()));
        model.addAttribute("businesses", businessService.findByProvider(currentUser.getId()));
        model.addAttribute("activitiesByBusiness", activitiesByBusiness);
        return "provider/businesses";
    }

    // Redes sociales de un emprendimiento del prestador (se muestran en el detalle de sus actividades)
    @GetMapping("/{id}/social")
    public String socialLinks(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        Business business = socialLinksService.findOwnBusiness(currentUser.getId(), id);
        model.addAttribute("business", business);
        model.addAttribute("form", new SocialLinksForm(business));
        return "provider/business-social";
    }

    @PostMapping("/{id}/social")
    public String updateSocialLinks(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                                    @ModelAttribute("form") SocialLinksForm form, BindingResult bindingResult,
                                    Model model, RedirectAttributes redirectAttributes) {
        try {
            Business business = socialLinksService.update(currentUser.getId(), id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Guardamos las redes sociales de \"" + business.getLegalName() + "\".");
            return "redirect:/provider/businesses";
        } catch (FieldValidationException e) {
            e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
        }
        model.addAttribute("business", socialLinksService.findOwnBusiness(currentUser.getId(), id));
        return "provider/business-social";
    }

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
                return "redirect:/provider/businesses";
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

    @ModelAttribute("banks")
    public Bank[] banks() {
        return Bank.values();
    }

    @ModelAttribute("locationsByDepartment")
    public Map<Department, List<Location>> locationsByDepartment() {
        return locationService.findAllGroupedByDepartment();
    }
}
