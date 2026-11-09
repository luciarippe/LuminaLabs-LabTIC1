package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.entities.Cancellation;
import uy.edu.um.luminalabs.entities.Payment;
import uy.edu.um.luminalabs.entities.PaymentStatus;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.CancellationService;
import uy.edu.um.luminalabs.services.ReservationService;

// CU-05: el turista cancela una reserva. Esta bajo /reservations, asi que solo entran turistas.
@Controller
@RequestMapping("/reservations/{id}/cancel")
@RequiredArgsConstructor
public class CancellationController {

    private final ReservationService reservationService;
    private final CancellationService cancellationService;
    private final ViewHelper view;

    // Pasos 1 y 2: se muestra la politica y cuanto se devolveria, antes de confirmar
    @GetMapping
    public String confirmation(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model,
                               RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("quote", cancellationService.quote(currentUser.getId(), id));
            model.addAttribute("reservation", reservationService.findOwn(currentUser.getId(), id));
            return "reservations/cancel";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reservations/" + id;
        }
    }

    // Pasos 3 a 9
    @PostMapping
    public String cancel(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            Cancellation cancellation = cancellationService.cancelByTourist(currentUser.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", resultMessage(cancellation));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reservations/" + id;
    }

    private String resultMessage(Cancellation cancellation) {
        Payment refund = cancellation.getRefundPayment();
        if (refund == null) {
            return "Cancelamos tu reserva y liberamos los lugares.";
        }
        String gateway = view.gatewayName();
        if (refund.getStatus() == PaymentStatus.APPROVED) {
            // El movimiento tal como lo informo la pasarela: la devolucion es un monto negativo
            return "Cancelamos tu reserva. " + capitalize(gateway) + " confirmó la devolución: "
                    + view.signedMoney(refund.getSignedAmount()) + " a la tarjeta terminada en "
                    + refund.getCardLastDigits() + ".";
        }
        // 8a: la reserva queda cancelada igual y la devolucion se reintenta sola
        return "Cancelamos tu reserva. La devolución de $ " + ViewHelper.money(cancellation.getRefundAmount())
                + " quedó en proceso: " + gateway + " no la confirmó todavía y la vamos a reintentar automáticamente.";
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
