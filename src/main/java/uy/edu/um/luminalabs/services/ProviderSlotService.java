package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.SlotForm;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.CancellationReason;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.SlotRescheduledEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.SlotNotFoundException;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// CU-04: el prestador agrega, modifica y cancela los slots (fechas y horarios) de sus actividades.
// Las reglas de cada slot estan en la entidad Slot; aca se verifica el acceso y lo que depende de otros slots.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderSlotService {

    // Reservas que ocupan lugares en el slot
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final ProviderActivityService activityService;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final CancellationService cancellationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public List<Slot> findSlots(Long providerId, Long activityId) {
        activityService.findOwnActivity(providerId, activityId);
        return slotRepository.findByActivityIdOrderByStartTimeAsc(activityId);
    }

    public Slot findOwnSlot(Long providerId, Long activityId, Long slotId) {
        return slotRepository.findWithActivityById(slotId)
                .filter(slot -> slot.getActivity().getId().equals(activityId))
                .filter(slot -> slot.getActivity().isManagedBy(providerId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));
    }

    @Transactional
    public Slot addSlot(Long providerId, Long activityId, SlotForm form) {
        Activity activity = activityService.findOwnActivity(providerId, activityId);
        ProviderActivityService.requireApprovedBusiness(activity);
        LocalDateTime startTime = validateStartTime(activity, form, null);
        Slot slot = activity.addSlot(new Slot(activity, startTime, form.getPrice(), form.getMaxCapacity()));
        return slotRepository.save(slot);
    }

    // 5a. Si cambia el horario y hay reservas, se marcan y se avisa a los turistas, que desde ese momento
    // pueden cancelar con devolucion total (CU-05 4b)
    @Transactional
    public Slot updateSlot(Long providerId, Long activityId, Long slotId, SlotForm form) {
        Slot slot = findOwnSlot(providerId, activityId, slotId);
        Activity activity = slot.getActivity();
        ProviderActivityService.requireApprovedBusiness(activity);
        LocalDateTime startTime = validateStartTime(activity, form, slot);
        // La entidad tambien lo controla; aca se revisa antes para mostrar el error junto al campo
        if (form.getMaxCapacity() < slot.getReservedSeats()) {
            throw new FieldValidationException(Map.of("maxCapacity",
                    "No puede ser menor que los " + slot.getReservedSeats() + " lugares ya reservados."));
        }
        LocalDateTime previousStart = slot.getStartTime();
        if (slot.update(startTime, form.getPrice(), form.getMaxCapacity(), LocalDateTime.now(clock))) {
            for (Reservation reservation : activeReservations(slot)) {
                reservation.markScheduleChanged();
                eventPublisher.publishEvent(new SlotRescheduledEvent(MailRecipient.from(reservation.getTourist()),
                        reservation.getId(), reservation.getCode(), activity.getName(), previousStart, startTime));
            }
        }
        return slot;
    }

    // 5b. Las reservas del slot se cancelan con devolucion total y se avisa a los turistas.
    // Devuelve cuantas se cancelaron.
    @Transactional
    public int cancelSlot(Long providerId, Long activityId, Long slotId) {
        Slot slot = findOwnSlot(providerId, activityId, slotId);
        ProviderActivityService.requireApprovedBusiness(slot.getActivity());
        slot.cancel(LocalDateTime.now(clock));
        return cancellationService.cancelWithFullRefund(activeReservations(slot), CancellationReason.SLOT_CANCELLED);
    }

    private List<Reservation> activeReservations(Slot slot) {
        return reservationRepository.findBySlot_IdAndStatusIn(slot.getId(), ACTIVE);
    }

    // CU-04 4: fecha y hora futuras y sin otro slot activo de la actividad en el mismo horario
    private LocalDateTime validateStartTime(Activity activity, SlotForm form, Slot current) {
        LocalDateTime startTime = form.getDate().atTime(form.getTime());
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new FieldValidationException(Map.of("date", "La fecha y hora tienen que ser futuras."));
        }
        if (activity.hasActiveSlotAt(startTime, current)) {
            throw new FieldValidationException(Map.of("time", "Ya hay un horario de esta actividad en esa fecha y hora."));
        }
        return startTime;
    }
}
