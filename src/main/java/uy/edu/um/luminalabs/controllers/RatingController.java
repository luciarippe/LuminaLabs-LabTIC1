package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.RatingForm;
import uy.edu.um.luminalabs.entities.Rating;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.RatingService;

// CU-08: el turista valora una reserva terminada. Esta bajo /reservations, asi que solo entran turistas,
// y RatingService verifica que la reserva sea suya (si no, 404).
@Controller
@RequestMapping("/reservations/{id}/rating")
@RequiredArgsConstructor
public class RatingController {

    private static final String ALREADY_RATED = "Ya valoraste esta experiencia. ¡Gracias por contarnos!";

    private final RatingService ratingService;

    // Pasos 1 a 3. 1a y 4a: si no se puede valorar, se vuelve al detalle de la reserva con el motivo
    @GetMapping
    public String form(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("reservation", ratingService.findRateable(currentUser.getId(), id));
            model.addAttribute("form", new RatingForm());
            return "reservations/rating";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reservations/" + id;
        }
    }

    // Pasos 4 a 6
    @PostMapping
    public String rate(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                       @Valid @ModelAttribute("form") RatingForm form, BindingResult bindingResult,
                       Model model, RedirectAttributes redirectAttributes) {
        try {
            Reservation reservation = ratingService.findRateable(currentUser.getId(), id);
            if (bindingResult.hasErrors()) {
                model.addAttribute("reservation", reservation);
                return "reservations/rating";
            }
            Rating rating = ratingService.rate(currentUser.getId(), id, form);
            redirectAttributes.addFlashAttribute("successMessage", "¡Gracias! Tu valoración ya se ve en la página de "
                    + rating.getActivity().getName() + ".");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // 4a con el formulario enviado dos veces a la vez: la base rechazo la segunda
            redirectAttributes.addFlashAttribute("errorMessage", ALREADY_RATED);
        }
        return "redirect:/reservations/" + id;
    }
}
