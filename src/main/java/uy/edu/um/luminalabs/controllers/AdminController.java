package uy.edu.um.luminalabs.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.AdminForm;
import uy.edu.um.luminalabs.dto.PasswordChangeForm;
import uy.edu.um.luminalabs.dto.ProfileForm;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.security.SessionUserRefresher;
import uy.edu.um.luminalabs.services.AccountService;
import uy.edu.um.luminalabs.services.AdminAccountService;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.BusinessStatusCounts;
import uy.edu.um.luminalabs.services.LocationService;

import java.util.List;
import java.util.function.Supplier;

// Seccion de administracion (solo ROLE_ADMIN, ver SecurityConfig)
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BusinessService businessService;
    private final AdminAccountService adminAccountService;
    private final AccountService accountService;
    private final SessionUserRefresher sessionUserRefresher;
    private final LocationService locationService;

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
    // q: busqueda por nombre o RUT/cedula. Con busqueda, las pestanas cuentan solo los resultados.
    @GetMapping("/businesses")
    public String businesses(@RequestParam(required = false) BusinessStatus status,
                             @RequestParam(defaultValue = "") String q, Model model) {
        String query = q.trim();
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", BusinessStatus.values());
        model.addAttribute("query", query);
        if (query.isEmpty()) {
            model.addAttribute("tabCounts", businessService.countByStatus());
            model.addAttribute("businesses", businessService.findByStatus(status));
        } else {
            List<Business> results = businessService.search(query);
            model.addAttribute("tabCounts", BusinessStatusCounts.of(results));
            model.addAttribute("businesses", results.stream()
                    .filter(business -> status == null || business.getStatus() == status)
                    .toList());
        }
        return "admin/businesses";
    }

    // Emprendimientos rechazados o suspendidos, con su motivo (misma pagina); q: busqueda por nombre o RUT/cedula
    @GetMapping("/businesses/denied")
    public String deniedBusinesses(@RequestParam(defaultValue = "") String q, Model model) {
        return businessesWithReason(BusinessStatus.DENIED, q, model);
    }

    @GetMapping("/businesses/suspended")
    public String suspendedBusinesses(@RequestParam(defaultValue = "") String q, Model model) {
        return businessesWithReason(BusinessStatus.SUSPENDED, q, model);
    }

    private String businessesWithReason(BusinessStatus status, String q, Model model) {
        String query = q.trim();
        model.addAttribute("status", status);
        model.addAttribute("query", query);
        model.addAttribute("businesses", businessService.findByStatusLatestFirst(status, query));
        return "admin/businesses-with-reason";
    }

    @GetMapping("/businesses/{id}")
    public String businessDetail(@PathVariable Long id, Model model) {
        model.addAttribute("business", businessService.findDetail(id));
        // CU-11 3a: se avisa cuantas reservas se cancelan antes de suspender
        model.addAttribute("activeReservations", businessService.countActiveReservations(id));
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

    // Configuracion > Localidades: la foto de cada una (se muestra en la portada y en las actividades sin fotos)
    @GetMapping("/locations")
    public String locations(Model model) {
        model.addAttribute("locationsByDepartment", locationService.findAllGroupedByDepartment());
        return "admin/locations";
    }

    @PostMapping("/locations/{id}/photo")
    public String uploadLocationPhoto(@PathVariable Long id, @RequestParam(value = "photo", required = false) MultipartFile photo,
                                      RedirectAttributes redirectAttributes) {
        try {
            Location location = locationService.setPhoto(id, photo);
            redirectAttributes.addFlashAttribute("successMessage", "Guardamos la foto de " + location.getName() + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations#location-" + id;
    }

    @PostMapping("/locations/{id}/photo/delete")
    public String removeLocationPhoto(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Location location = locationService.removePhoto(id);
            redirectAttributes.addFlashAttribute("successMessage", "Quitamos la foto de " + location.getName() + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations#location-" + id;
    }

    // Usuarios > Nuevo administrador
    @GetMapping("/admins/new")
    public String newAdmin(Model model) {
        model.addAttribute("form", new AdminForm());
        return "admin/admin-new";
    }

    @PostMapping("/admins/new")
    public String createAdmin(@Valid @ModelAttribute("form") AdminForm form, BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Admin admin = adminAccountService.create(form);
                redirectAttributes.addFlashAttribute("successMessage", "Se creó el administrador \""
                        + admin.getUsername() + "\". Le enviamos por correo su usuario y contraseña.");
                return "redirect:/admin/admins/new";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        // No se devuelve la contrasena al formulario
        form.setPassword(null);
        form.setConfirmPassword(null);
        return "admin/admin-new";
    }

    // Usuarios > Mi cuenta: datos propios y cambio de contrasena (dos formularios en la misma pagina)
    @GetMapping("/account")
    public String account(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        model.addAttribute("profileForm", accountService.profileOf(currentUser.getId()));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return accountPage(currentUser, model);
    }

    @PostMapping("/account/profile")
    public String updateProfile(@AuthenticationPrincipal SecurityUser currentUser,
                                @Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult bindingResult,
                                Model model, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        if (!bindingResult.hasErrors()) {
            try {
                User user = accountService.updateProfile(currentUser.getId(), form);
                sessionUserRefresher.refresh(user, request, response);
                redirectAttributes.addFlashAttribute("successMessage", "Tus datos se guardaron.");
                return "redirect:/admin/account";
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return accountPage(currentUser, model);
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal SecurityUser currentUser,
                                 @Valid @ModelAttribute("passwordForm") PasswordChangeForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                accountService.changePassword(currentUser.getId(), form);
                redirectAttributes.addFlashAttribute("successMessage", "Tu contraseña se cambió.");
                return "redirect:/admin/account";
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
        model.addAttribute("admin", accountService.findUser(currentUser.getId()));
        return "admin/account";
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
