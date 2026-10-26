package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.security.UserSessionTerminator;
import uy.edu.um.luminalabs.services.UserAdministrationService;

// Administracion > Usuarios: todas las cuentas, su ficha y bloquear o desbloquear (solo ROLE_ADMIN, ver SecurityConfig)
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdministrationService userService;
    private final UserSessionTerminator sessionTerminator;

    @GetMapping
    public String list(@RequestParam(defaultValue = "ALL") UserAdministrationService.Filter filter,
                       @RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("search", userService.search(filter, q));
        model.addAttribute("filter", filter);
        model.addAttribute("query", q.trim());
        return "admin/users";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        model.addAttribute("detail", userService.detail(id));
        model.addAttribute("isMe", id.equals(currentUser.getId()));
        return "admin/user-detail";
    }

    // Ademas de no dejarla iniciar sesion, se cierran las sesiones que tenga abiertas
    @PostMapping("/{id}/block")
    public String block(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                        @RequestParam(defaultValue = "") String reason, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.block(currentUser.getId(), id, reason);
            sessionTerminator.terminateSessionsOf(id);
            redirectAttributes.addFlashAttribute("successMessage", "Bloqueamos la cuenta de " + user.getFullName()
                    + ". Ya no puede iniciar sesión y le avisamos por correo con el motivo.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/unblock")
    public String unblock(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.unblock(id);
            redirectAttributes.addFlashAttribute("successMessage", "La cuenta de " + user.getFullName()
                    + " está activa de nuevo. Le avisamos por correo.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }
}
