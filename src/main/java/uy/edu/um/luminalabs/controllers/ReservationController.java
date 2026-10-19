package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.ReservationForm;
import uy.edu.um.luminalabs.entities.Payment;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.ReservationNotPossibleException;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.CancellationService;
import uy.edu.um.luminalabs.services.PaymentService;
import uy.edu.um.luminalabs.services.ReservationService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Reservas del turista: reservar (CU-01) y consultar sus reservas (CU-13).
// /reservations/** solo acepta ROLE_TOURIST (ver SecurityConfig). Si un visitante sin sesion intenta reservar,
// Spring Security lo manda al login y despues lo devuelve aca (CU-07 7b, ver RoleBasedSuccessHandler).
@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final CancellationService cancellationService;
    private final Clock clock;

    // ---------- Reservar (CU-01) ----------

    // Pasos 2 y 3: el turista eligio un slot y la cantidad de personas en el detalle de la actividad
    @GetMapping("/new")
    public String newReservation(@AuthenticationPrincipal SecurityUser currentUser, @RequestParam Long slotId,
                                 @RequestParam(defaultValue = "1") int participants, Model model,
                                 RedirectAttributes redirectAttributes) {
        int count = Math.clamp(participants, 1, ReservationForm.MAX_PARTICIPANTS);
        try {
            Slot slot = reservationService.findBookableSlot(slotId, count);
            model.addAttribute("form", reservationService.newForm(currentUser.getId(), slotId, count));
            return showForm(slot, count, model);
        } catch (ReservationNotPossibleException e) {
            return backToActivity(e, redirectAttributes);
        }
    }

    @PostMapping
    public String create(@AuthenticationPrincipal SecurityUser currentUser,
                         @Valid @ModelAttribute("form") ReservationForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        try {
            if (!bindingResult.hasErrors()) {
                try {
                    Reservation reservation = reservationService.create(currentUser.getId(), form);
                    redirectAttributes.addFlashAttribute("successMessage", "¡Listo! Guardamos tu reserva "
                            + reservation.getCode() + " y te reservamos los lugares hasta las "
                            + reservation.getPaymentDeadline().format(TIME) + " para que completes el pago.");
                    return "redirect:/reservations/" + reservation.getId();
                } catch (FieldValidationException e) {
                    e.getFieldErrors().forEach((field, message) -> bindingResult.rejectValue(field, "invalid", message));
                }
            }
            int count = Math.max(form.getParticipants().size(), 1);
            return showForm(reservationService.findBookableSlot(form.getSlotId(), count), count, model);
        } catch (ReservationNotPossibleException e) {
            return backToActivity(e, redirectAttributes);
        }
    }

    private String showForm(Slot slot, int participants, Model model) {
        model.addAttribute("slot", slot);
        model.addAttribute("activity", slot.getActivity());
        model.addAttribute("participantCount", participants);
        model.addAttribute("maxParticipants", ReservationForm.maxParticipantsFor(slot));
        model.addAttribute("total", slot.getPrice().multiply(BigDecimal.valueOf(participants)));
        return "reservations/new";
    }

    // 4a y 5a: se vuelve al detalle de la actividad, que muestra los demas slots
    private static String backToActivity(ReservationNotPossibleException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/activities/" + e.getActivityId();
    }

    // ---------- Mis reservas (CU-13) ----------

    // Paso 2: proximas (de la mas cercana a la mas lejana) y pasadas (de la mas reciente a la mas antigua)
    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Boolean, List<Reservation>> byUpcoming = reservationService.findByTourist(currentUser.getId()).stream()
                .collect(Collectors.partitioningBy(reservation -> reservation.isUpcoming(now)));
        model.addAttribute("upcoming", byUpcoming.get(true));
        model.addAttribute("past", byUpcoming.get(false).reversed());
        return "reservations/list";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        Reservation reservation = reservationService.findOwn(currentUser.getId(), id);
        List<Payment> payments = paymentService.findPayments(id);
        model.addAttribute("reservation", reservation);
        model.addAttribute("payments", payments);
        // Con un intento sin respuesta no se ofrece pagar de nuevo, sino verificar ese pago (RF-18)
        model.addAttribute("awaitingPayment", payments.stream().anyMatch(Payment::isAwaitingResponse));
        model.addAttribute("cancellation", cancellationService.findCancellation(id).orElse(null));
        LocalDateTime now = LocalDateTime.now(clock);
        // CU-13 paso 6: se puede cancelar si esta vigente y el slot no empezo (CU-05)
        model.addAttribute("canCancel", reservation.isActive() && !reservation.getSlot().hasStarted(now));
        model.addAttribute("now", now);
        return "reservations/detail";
    }
}
