package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.ParticipantForm;
import uy.edu.um.luminalabs.dto.ReservationForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.exceptions.ReservationNotPossibleException;
import uy.edu.um.luminalabs.exceptions.SlotNotFoundException;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reservas del turista: crear una reserva (CU-01 pasos 1 a 6) y consultarlas (CU-13).
// El pago (CU-01 pasos 7 a 10) se agrega en la siguiente etapa: por ahora la reserva queda pendiente de pago.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // Slot que el turista eligio, verificando que se pueda reservar (para mostrar el formulario)
    public Slot findBookableSlot(Long slotId, int participants) {
        Slot slot = slotRepository.findWithActivityById(slotId).orElseThrow(() -> new SlotNotFoundException(slotId));
        requireBookable(slot, participants, LocalDateTime.now(clock));
        return slot;
    }

    // Formulario con "participants" filas vacias; la primera con los datos del turista, que suele ser uno de ellos
    public ReservationForm newForm(Long touristId, Long slotId, int participants) {
        Tourist tourist = findTourist(touristId);
        ReservationForm form = new ReservationForm();
        form.setSlotId(slotId);
        form.getParticipants().add(new ParticipantForm(tourist.getFirstName(), tourist.getLastName(), tourist.getBirthDate()));
        for (int i = 1; i < participants; i++) {
            form.getParticipants().add(new ParticipantForm());
        }
        return form;
    }

    // CU-01 pasos 3 a 6. El slot se lee con bloqueo pesimista (ver SlotRepository.findByIdForUpdate):
    // si dos turistas reservan el mismo slot a la vez, el segundo espera y ve la disponibilidad actualizada.
    @Transactional
    public Reservation create(Long touristId, ReservationForm form) {
        LocalDateTime now = LocalDateTime.now(clock);
        Slot slot = slotRepository.findByIdForUpdate(form.getSlotId())
                .orElseThrow(() -> new SlotNotFoundException(form.getSlotId()));
        Activity activity = slot.getActivity();

        // 3a: requisito de edad, contado al dia de la actividad
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate activityDate = slot.getStartTime().toLocalDate();
        List<ParticipantForm> participantForms = form.getParticipants();
        for (int i = 0; i < participantForms.size(); i++) {
            if (!activity.allowsParticipantBornOn(participantForms.get(i).getBirthDate(), activityDate)) {
                errors.put("participants[" + i + "].birthDate",
                        "Esta actividad requiere tener al menos " + activity.getMinimumAge() + " años el día de la actividad.");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        // 4a y 5a: disponibilidad y plazo de reserva
        requireBookable(slot, participantForms.size(), now);

        List<Participant> participants = new ArrayList<>();
        for (ParticipantForm participant : participantForms) {
            participants.add(new Participant(participant.getFirstName().trim(), participant.getLastName().trim(),
                    participant.getBirthDate()));
        }
        // 6: el constructor descuenta los lugares del slot y congela el precio
        return reservationRepository.save(new Reservation(findTourist(touristId), slot, participants, now));
    }

    // CU-13: reservas del turista ordenadas por fecha del slot
    public List<Reservation> findByTourist(Long touristId) {
        return reservationRepository.findByTourist_IdOrderBySlot_StartTimeAsc(touristId);
    }

    // CU-13 paso 2: las reservas del turista separadas para "Mis reservas". Las pendientes de pago van aparte
    // para destacarlas; las canceladas y vencidas pasan al historial aunque su horario no haya llegado.
    public TouristReservations findOverview(Long touristId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Reservation> pendingPayment = new ArrayList<>();
        List<Reservation> upcoming = new ArrayList<>();
        List<Reservation> history = new ArrayList<>();
        for (Reservation reservation : findByTourist(touristId)) {
            if (reservation.isActive() && reservation.isUpcoming(now)) {
                (reservation.isPendingPayment() ? pendingPayment : upcoming).add(reservation);
            } else {
                history.add(reservation);
            }
        }
        // Historial: la mas reciente primero
        return new TouristReservations(pendingPayment, upcoming, history.reversed(), now);
    }

    // Reservas de "Mis reservas", cada lista ordenada como se muestra. "now" es el momento de la consulta.
    public record TouristReservations(List<Reservation> pendingPayment, List<Reservation> upcoming,
                                      List<Reservation> history, LocalDateTime now) {

        public boolean isEmpty() {
            return pendingPayment.isEmpty() && upcoming.isEmpty() && history.isEmpty();
        }

        // Dias de calendario hasta la fecha del horario (0 = hoy)
        public long daysUntil(Reservation reservation) {
            return ChronoUnit.DAYS.between(now.toLocalDate(), reservation.getSlot().getStartTime().toLocalDate());
        }

        // Minutos que le quedan para pagar una reserva pendiente (0 si el plazo ya paso)
        public long minutesToPay(Reservation reservation) {
            return Math.max(0, ChronoUnit.MINUTES.between(now, reservation.getPaymentDeadline()));
        }

        public long finishedCount() {
            return history.stream().filter(reservation -> reservation.getStatus() == ReservationStatus.FINISHED).count();
        }
    }

    // RNF-10: la reserva de otro turista se trata como inexistente (404)
    public Reservation findOwn(Long touristId, Long reservationId) {
        return reservationRepository.findWithDetailsById(reservationId)
                .filter(reservation -> reservation.getTourist().getId().equals(touristId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    // CU-18 paso 3: las confirmadas cuyo slot ya termino (inicio + duracion) pasan a terminadas, y se pueden valorar
    @Transactional
    public int finishEndedReservations() {
        LocalDateTime now = LocalDateTime.now(clock);
        int finished = 0;
        for (Reservation reservation : reservationRepository.findByStatusAndSlot_StartTimeBefore(ReservationStatus.CONFIRMED, now)) {
            if (!reservation.getEndTime().isAfter(now)) {
                reservation.finish();
                finished++;
            }
        }
        return finished;
    }

    private void requireBookable(Slot slot, int participants, LocalDateTime now) {
        Long activityId = slot.getActivity().getId();
        if (!slot.isActive() || !slot.getActivity().getBusiness().isApproved()) {
            throw new ReservationNotPossibleException("Ese horario ya no está disponible. Elegí otro.", activityId);
        }
        if (!slot.isBookableAt(now) && slot.getAvailability() > 0) {
            int cutoff = slot.getActivity().getBookingCutoffMinutes();
            throw new ReservationNotPossibleException(cutoff == 0
                    ? "Ese horario ya comenzó. Elegí otro."
                    : "Este horario se puede reservar hasta " + cutoff + " minutos antes del inicio. Elegí otro.",
                    activityId);
        }
        if (participants > slot.getAvailability()) {
            throw new ReservationNotPossibleException(slot.isSoldOut()
                    ? "Ese horario está completo. Elegí otro."
                    : "Para ese horario quedan " + slot.getAvailability() + " lugares. Elegí otro horario o menos personas.",
                    activityId);
        }
    }

    private Tourist findTourist(Long touristId) {
        return userRepository.findById(touristId)
                .filter(user -> user instanceof Tourist)
                .map(user -> (Tourist) user)
                .orElseThrow(() -> new IllegalStateException("Tourist not found: " + touristId));
    }
}
