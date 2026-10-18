package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.CardForm;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.exceptions.PaymentNotPossibleException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.PaymentService;
import uy.edu.um.luminalabs.services.ReservationService;

import java.time.Clock;
import java.time.LocalDateTime;

// Pago de una reserva (CU-01 pasos 7 a 10 y CU-16). Esta bajo /reservations, asi que solo entran turistas.
@Controller
@RequestMapping("/reservations/{id}")
@RequiredArgsConstructor
public class PaymentController {

    private static final String NO_RESPONSE_MESSAGE = "La pasarela de pagos no respondió a tiempo, así que todavía no "
            + "sabemos si el pago se aprobó. No vuelvas a pagar: en unos segundos tocá \"Verificar el pago\" para consultarlo.";

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final Clock clock;

    @GetMapping("/pay")
    public String payForm(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        Reservation reservation = reservationService.findOwn(currentUser.getId(), id);
        if (!reservation.isPendingPayment()) {
            return "redirect:/reservations/" + id;
        }
        model.addAttribute("cardForm", new CardForm());
        return showPayForm(reservation, model);
    }

    @PostMapping("/pay")
    public String pay(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                      @Valid @ModelAttribute("cardForm") CardForm cardForm, BindingResult bindingResult,
                      Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return showPayForm(reservationService.findOwn(currentUser.getId(), id), model);
        }
        try {
            switch (paymentService.pay(currentUser.getId(), id, cardForm)) {
                case CONFIRMED -> redirectAttributes.addFlashAttribute("successMessage",
                        "¡Pago aprobado! Tu reserva está confirmada. Te mandamos los detalles por correo.");
                case NO_RESPONSE -> redirectAttributes.addFlashAttribute("warningMessage", NO_RESPONSE_MESSAGE);
                case REJECTED -> {
                    // 9a: se puede reintentar con otra tarjeta mientras no venza el plazo
                    model.addAttribute("errorMessage", "El pago no fue aprobado. Revisá los datos o probá con otra "
                            + "tarjeta. Tus lugares siguen reservados hasta que venza el plazo.");
                    cardForm.setCardNumber(null);
                    cardForm.setCvv(null);
                    return showPayForm(reservationService.findOwn(currentUser.getId(), id), model);
                }
            }
        } catch (PaymentNotPossibleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reservations/" + id;
    }

    // CU-16 5a: el turista pide verificar un pago que quedo sin respuesta
    @PostMapping("/payments/check")
    public String checkPayment(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        switch (paymentService.checkPendingPayment(currentUser.getId(), id)) {
            case CONFIRMED -> redirectAttributes.addFlashAttribute("successMessage",
                    "Confirmamos con la pasarela que el pago se aprobó. Tu reserva está confirmada.");
            case REJECTED -> redirectAttributes.addFlashAttribute("errorMessage",
                    "La pasarela nos confirmó que el pago anterior no se cobró. Podés volver a intentarlo.");
            case NO_RESPONSE -> redirectAttributes.addFlashAttribute("warningMessage",
                    "Todavía no pudimos confirmar el pago con la pasarela. Probá de nuevo en unos segundos.");
        }
        return "redirect:/reservations/" + id;
    }

    private String showPayForm(Reservation reservation, Model model) {
        model.addAttribute("reservation", reservation);
        model.addAttribute("now", LocalDateTime.now(clock));
        return "reservations/pay";
    }
}
