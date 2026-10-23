package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.CardForm;
import uy.edu.um.luminalabs.dto.CardTokenForm;
import uy.edu.um.luminalabs.dto.PaymentCardForm;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.exceptions.PaymentNotPossibleException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.PaymentService;
import uy.edu.um.luminalabs.services.ReservationService;

import java.time.Clock;
import java.time.LocalDateTime;

// Pago de una reserva (CU-01 pasos 7 a 10 y CU-16). Esta bajo /reservations, asi que solo entran turistas.
// La pantalla depende de la pasarela (app.payments.provider):
//  - mock: nuestro formulario de tarjeta, que se manda a POST /pay;
//  - mercadopago: el formulario de Mercado Pago (Card Payment Brick), que manda solo un token a POST /pay/token.
@Controller
@RequestMapping("/reservations/{id}")
public class PaymentController {

    private static final String MOCK = "mock";
    private static final String MERCADO_PAGO = "mercadopago";

    private static final String NO_RESPONSE_MESSAGE = "La pasarela de pagos todavía no nos confirmó si el pago se "
            + "aprobó. No vuelvas a pagar: en unos segundos tocá \"Verificar el pago\" para consultarlo.";
    private static final String REJECTED_MESSAGE = "El pago no fue aprobado. Revisá los datos o probá con otra "
            + "tarjeta. Tus lugares siguen reservados hasta que venza el plazo.";

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final Clock clock;
    private final String provider;
    // La Public Key no es secreta: Mercado Pago la usa en el navegador para generar el token de la tarjeta
    private final String mercadoPagoPublicKey;

    public PaymentController(ReservationService reservationService, PaymentService paymentService, Clock clock,
                             @Value("${app.payments.provider:mock}") String provider,
                             @Value("${app.mercadopago.public-key:}") String mercadoPagoPublicKey) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.clock = clock;
        this.provider = provider;
        this.mercadoPagoPublicKey = mercadoPagoPublicKey;
    }

    @GetMapping("/pay")
    public String payForm(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        Reservation reservation = reservationService.findOwn(currentUser.getId(), id);
        if (!reservation.isPendingPayment()) {
            return "redirect:/reservations/" + id;
        }
        model.addAttribute("cardForm", new CardForm());
        return showPayForm(reservation, model);
    }

    // Pasarela simulada: llegan los datos de la tarjeta
    @PostMapping("/pay")
    public String pay(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                      @Valid @ModelAttribute("cardForm") CardForm cardForm, BindingResult bindingResult,
                      Model model, RedirectAttributes redirectAttributes) {
        requireProvider(MOCK);
        if (bindingResult.hasErrors()) {
            return showPayForm(reservationService.findOwn(currentUser.getId(), id), model);
        }
        return processPayment(currentUser, id, cardForm, model, redirectAttributes, () -> {
            cardForm.setCardNumber(null);
            cardForm.setCvv(null);
        });
    }

    // Mercado Pago: llega el token que genero el Brick, nunca el numero de tarjeta
    @PostMapping("/pay/token")
    public String payWithToken(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                               @Valid @ModelAttribute CardTokenForm cardTokenForm, BindingResult bindingResult,
                               Model model, RedirectAttributes redirectAttributes) {
        requireProvider(MERCADO_PAGO);
        if (bindingResult.hasErrors()) {
            // Los campos los llena el Brick: si falta alguno, el turista solo puede volver a completar la tarjeta
            model.addAttribute("errorMessage", "No pudimos leer los datos de la tarjeta. Volvé a completarlos.");
            return showPayForm(reservationService.findOwn(currentUser.getId(), id), model);
        }
        // El token sirve para un solo cobro: si se rechaza, el Brick se vuelve a mostrar vacio
        return processPayment(currentUser, id, cardTokenForm, model, redirectAttributes, () -> {
        });
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

    // onRejected limpia lo que no se debe volver a mostrar antes de presentar otra vez el formulario
    private String processPayment(SecurityUser currentUser, Long id, PaymentCardForm card, Model model,
                                  RedirectAttributes redirectAttributes, Runnable onRejected) {
        try {
            switch (paymentService.pay(currentUser.getId(), id, card)) {
                case CONFIRMED -> redirectAttributes.addFlashAttribute("successMessage",
                        "¡Pago aprobado! Tu reserva está confirmada. Te mandamos los detalles por correo.");
                case NO_RESPONSE -> redirectAttributes.addFlashAttribute("warningMessage", NO_RESPONSE_MESSAGE);
                case REJECTED -> {
                    // 9a: se puede reintentar con otra tarjeta mientras no venza el plazo
                    model.addAttribute("errorMessage", REJECTED_MESSAGE);
                    onRejected.run();
                    return showPayForm(reservationService.findOwn(currentUser.getId(), id), model);
                }
            }
        } catch (PaymentNotPossibleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reservations/" + id;
    }

    // Cada pasarela recibe la tarjeta de una forma distinta: el otro formulario no se acepta
    private void requireProvider(String expected) {
        if (!provider.equals(expected)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String showPayForm(Reservation reservation, Model model) {
        model.addAttribute("reservation", reservation);
        model.addAttribute("now", LocalDateTime.now(clock));
        model.addAttribute("mercadoPago", provider.equals(MERCADO_PAGO));
        model.addAttribute("mercadoPagoPublicKey", mercadoPagoPublicKey);
        return "reservations/pay";
    }
}
