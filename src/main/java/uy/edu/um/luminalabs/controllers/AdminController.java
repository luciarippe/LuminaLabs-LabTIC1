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
import uy.edu.um.luminalabs.dto.CategoryForm;
import uy.edu.um.luminalabs.dto.LocationForm;
import uy.edu.um.luminalabs.dto.PasswordChangeForm;
import uy.edu.um.luminalabs.dto.ProfileForm;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.security.SessionUserRefresher;
import uy.edu.um.luminalabs.services.AccountService;
import uy.edu.um.luminalabs.services.AdminAccountService;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.BusinessStatusCounts;
import uy.edu.um.luminalabs.services.CategoryService;
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
    private final CategoryService categoryService;

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

    // Configuracion > Localidades (CU-10, RF-24). Primero la lista de departamentos; al entrar a uno, su foto y sus
    // localidades (alta, modificacion y eliminacion).
    @GetMapping("/locations")
    public String locations(Model model) {
        model.addAttribute("departments", locationService.summarizeDepartments());
        return "admin/locations";
    }

    // edit: la localidad que se esta modificando (sin edit, el formulario es para una nueva en este departamento)
    @GetMapping("/locations/{department}")
    public String departmentLocations(@PathVariable Department department, @RequestParam(required = false) Long edit,
                                      Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("form", edit == null ? new LocationForm(department) : new LocationForm(locationService.find(edit)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/locations/" + department;
        }
        return departmentPage(department, edit, model);
    }

    @PostMapping("/locations")
    public String createLocation(@Valid @ModelAttribute("form") LocationForm form, BindingResult bindingResult,
                                 Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Location location = locationService.create(form);
                redirectAttributes.addFlashAttribute("successMessage", "Agregamos " + location.getName()
                        + ". Ya se puede elegir al publicar actividades y registrar emprendimientos.");
                return "redirect:/admin/locations/" + location.getDepartment() + "#location-" + location.getId();
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        if (form.getDepartment() == null) {
            return "redirect:/admin/locations";
        }
        return departmentPage(form.getDepartment(), null, model);
    }

    // 7a: el cambio se ve en todas las actividades y emprendimientos que la usan
    @PostMapping("/locations/{id}")
    public String updateLocation(@PathVariable Long id, @Valid @ModelAttribute("form") LocationForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Location location = locationService.update(id, form);
                redirectAttributes.addFlashAttribute("successMessage", "Guardamos los cambios de " + location.getName()
                        + ". Se ven en todas las actividades y emprendimientos que la usan.");
                return "redirect:/admin/locations/" + location.getDepartment() + "#location-" + id;
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/admin/locations";
            }
        }
        // Se vuelve al departamento donde estaba la localidad (el del formulario puede ser el nuevo)
        return departmentPage(locationService.find(id).getDepartment(), id, model);
    }

    // 6b: si esta en uso no se elimina y se informa cuantas actividades y emprendimientos la usan
    @PostMapping("/locations/{id}/delete")
    public String deleteLocation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String page = locationPage(id);
        try {
            Location location = locationService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Eliminamos " + location.getName() + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + page;
    }

    // Foto del departamento: se muestra en "Destinos" de la portada y en las actividades sin fotos propias
    @PostMapping("/departments/{department}/photo")
    public String uploadDepartmentPhoto(@PathVariable Department department,
                                        @RequestParam(value = "photo", required = false) MultipartFile photo,
                                        RedirectAttributes redirectAttributes) {
        try {
            locationService.setDepartmentPhoto(department, photo);
            redirectAttributes.addFlashAttribute("successMessage", "Guardamos la foto de " + department.getLabel()
                    + ". Ya se ve en los destinos de la portada y en sus actividades sin fotos.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + department;
    }

    @PostMapping("/departments/{department}/photo/delete")
    public String removeDepartmentPhoto(@PathVariable Department department, RedirectAttributes redirectAttributes) {
        locationService.removeDepartmentPhoto(department);
        redirectAttributes.addFlashAttribute("successMessage", "Quitamos la foto de " + department.getLabel()
                + ". En su lugar se va a ver un paisaje dibujado.");
        return "redirect:/admin/locations/" + department;
    }

    private String departmentPage(Department department, Long editingId, Model model) {
        model.addAttribute("department", department);
        model.addAttribute("departmentPhotoId", locationService.findDepartmentPhoto(department));
        model.addAttribute("departments", Department.values());
        model.addAttribute("locations", locationService.findByDepartment(department));
        model.addAttribute("editingId", editingId);
        return "admin/department-locations";
    }

    // La pagina del departamento de la localidad, o la lista si ya no existe
    private String locationPage(Long id) {
        try {
            Location location = locationService.find(id);
            return "/admin/locations/" + location.getDepartment();
        } catch (IllegalArgumentException e) {
            return "/admin/locations";
        }
    }

    // Configuracion > Categorias (CU-10, RF-24): listado con alta a la derecha; modificar usa la misma pagina
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("form", new CategoryForm());
        return categoriesPage(null, model);
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("form", new CategoryForm(categoryService.find(id)));
            return categoriesPage(id, model);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("form") CategoryForm form, BindingResult bindingResult,
                                 Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ActivityCategory category = categoryService.create(form);
                redirectAttributes.addFlashAttribute("successMessage", "Agregamos la categoría \"" + category.getName()
                        + "\". Los prestadores ya la pueden elegir al publicar.");
                return "redirect:/admin/categories#category-" + category.getId();
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            }
        }
        return categoriesPage(null, model);
    }

    // 7a: el cambio se ve en todas las actividades que la usan
    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable Long id, @Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ActivityCategory category = categoryService.update(id, form);
                redirectAttributes.addFlashAttribute("successMessage", "Guardamos los cambios de \"" + category.getName()
                        + "\". Se ven en todas las actividades que la usan.");
                return "redirect:/admin/categories#category-" + id;
            } catch (FieldValidationException e) {
                e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/admin/categories";
            }
        }
        return categoriesPage(id, model);
    }

    // 6b: si esta en uso no se elimina y se informa cuantas actividades la usan
    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ActivityCategory category = categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Eliminamos la categoría \"" + category.getName() + "\".");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    // editingId: la categoria que se esta modificando (null = el formulario es para una nueva)
    private String categoriesPage(Long editingId, Model model) {
        model.addAttribute("categories", categoryService.findAllWithUsage());
        model.addAttribute("editingId", editingId);
        return "admin/categories";
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
