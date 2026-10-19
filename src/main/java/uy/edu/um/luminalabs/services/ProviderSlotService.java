package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.SlotForm;
import uy.edu.um.luminalabs.dto.SlotRepetition;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.CancellationReason;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.ReservationStatus;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.entities.SlotStatus;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.SlotRescheduledEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.SlotNotFoundException;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// CU-04: el prestador agrega, modifica y cancela los slots (fechas y horarios) de sus actividades.
// Las reglas de cada slot estan en la entidad Slot; aca se verifica el acceso y lo que depende de otros slots.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderSlotService {

    // Reservas que ocupan lugares en el slot
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Se avisa al prestador cuando el ultimo horario de una actividad es dentro de menos de estos dias
    public static final int RUNNING_OUT_DAYS = 30;

    private final ProviderActivityService activityService;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final CancellationService cancellationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Panel del prestador: los proximos horarios activos de todas sus actividades
    public List<Slot> findUpcomingSlots(Long providerId, int limit) {
        return slotRepository.findUpcomingForProvider(providerId, SlotStatus.ACTIVE, LocalDateTime.now(clock),
                PageRequest.of(0, limit));
    }

    public SlotRepository.UpcomingTotals upcomingTotals(Long providerId) {
        return slotRepository.countUpcomingForProvider(providerId, SlotStatus.ACTIVE, LocalDateTime.now(clock));
    }

    // Proxima fecha y precio "desde" de cada actividad (clave: id de la actividad); sin horarios no aparece
    public Map<Long, SlotRepository.SlotSummary> summarize(List<Activity> activities) {
        if (activities.isEmpty()) {
            return Map.of();
        }
        return slotRepository.summarize(activities.stream().map(Activity::getId).toList(), SlotStatus.ACTIVE,
                        LocalDateTime.now(clock)).stream()
                .collect(Collectors.toMap(SlotRepository.SlotSummary::getActivityId, Function.identity()));
    }

    // Actividades a las que se les terminan los horarios: de emprendimientos aprobados (en los demas no se puede
    // cargar) y sin horarios proximos o con el ultimo dentro de RUNNING_OUT_DAYS. Las que no tienen horarios
    // primero; despues, la que termina antes.
    public List<RunningOut> findRunningOut(List<Activity> activities) {
        List<Activity> publishable = activities.stream().filter(activity -> activity.getBusiness().isApproved()).toList();
        Map<Long, SlotRepository.SlotSummary> summaries = summarize(publishable);
        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.plusDays(RUNNING_OUT_DAYS);
        return publishable.stream()
                .map(activity -> {
                    SlotRepository.SlotSummary summary = summaries.get(activity.getId());
                    LocalDateTime lastStart = summary == null ? null : summary.getLastStart();
                    long daysLeft = lastStart == null ? 0 : ChronoUnit.DAYS.between(today, lastStart.toLocalDate());
                    return new RunningOut(activity, lastStart, daysLeft);
                })
                .filter(item -> item.lastStart() == null || item.lastStart().toLocalDate().isBefore(limit))
                .sorted(Comparator.comparing(RunningOut::lastStart, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    public List<RunningOut> findRunningOut(Long providerId) {
        return findRunningOut(activityService.findActivities(providerId));
    }

    // Una actividad con los horarios por terminarse. lastStart null = no le quedan horarios proximos.
    public record RunningOut(Activity activity, LocalDateTime lastStart, long daysLeft) {
        public boolean hasNoSlots() {
            return lastStart == null;
        }
    }

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

    // Agrega el horario del formulario y, si se pide, sus repeticiones hasta form.repeatUntil. Las fechas que ya
    // tienen un horario activo a esa hora se saltean (y se informan) en vez de rechazar todo.
    @Transactional
    public AddedSlots addSlots(Long providerId, Long activityId, SlotForm form) {
        if (!form.isRepeated()) {
            return new AddedSlots(List.of(addSlot(providerId, activityId, form)), 0);
        }
        Activity activity = activityService.findOwnActivity(providerId, activityId);
        ProviderActivityService.requireApprovedBusiness(activity);
        validateStartTime(activity, form.getDate().atTime(form.getTime()), null, false);
        List<LocalDate> dates = repetitionDates(form);

        List<Slot> created = new ArrayList<>();
        int skipped = 0;
        for (LocalDate date : dates) {
            LocalDateTime startTime = date.atTime(form.getTime());
            if (activity.hasActiveSlotAt(startTime, null)) {
                skipped++;
            } else {
                created.add(activity.addSlot(new Slot(activity, startTime, form.getPrice(), form.getMaxCapacity())));
            }
        }
        if (created.isEmpty()) {
            throw new FieldValidationException(Map.of("time",
                    "Todas esas fechas ya tienen un horario de esta actividad a esa hora."));
        }
        return new AddedSlots(slotRepository.saveAll(created), skipped);
    }

    // Fechas de la repeticion, con sus reglas: fecha final obligatoria, posterior a la primera, dentro del limite
    // de meses y sin pasar el tope de horarios por vez
    private List<LocalDate> repetitionDates(SlotForm form) {
        LocalDate until = form.getRepeatUntil();
        if (until == null) {
            throw new FieldValidationException(Map.of("repeatUntil", "Elegí hasta qué fecha se repite."));
        }
        if (!until.isAfter(form.getDate())) {
            throw new FieldValidationException(Map.of("repeatUntil", "Tiene que ser posterior a la fecha del primer horario."));
        }
        if (until.isAfter(lastAllowedDate())) {
            throw new FieldValidationException(Map.of("repeatUntil", "Podés cargar horarios hasta "
                    + SlotForm.MAX_MONTHS_AHEAD + " meses adelante (hasta el " + lastAllowedDate().format(DATE) + ")."));
        }
        if (form.getRepetition() == SlotRepetition.WEEKDAYS && form.getWeekdays().isEmpty()) {
            throw new FieldValidationException(Map.of("weekdays", "Elegí al menos un día de la semana."));
        }
        List<LocalDate> dates = form.getRepetition().dates(form.getDate(), until, form.getWeekdays());
        if (dates.isEmpty()) {
            throw new FieldValidationException(Map.of("weekdays", "Ningún día elegido cae entre esas fechas."));
        }
        if (dates.size() > SlotForm.MAX_SLOTS_PER_REPETITION) {
            throw new FieldValidationException(Map.of("repeatUntil", "Serían " + dates.size()
                    + " horarios; el máximo por vez es " + SlotForm.MAX_SLOTS_PER_REPETITION + ". Elegí una fecha final más cercana."));
        }
        return dates;
    }

    // Ultimo dia en que se puede cargar un horario (SlotForm.MAX_MONTHS_AHEAD desde hoy)
    public LocalDate lastAllowedDate() {
        return LocalDate.now(clock).plusMonths(SlotForm.MAX_MONTHS_AHEAD);
    }

    // Horarios creados de una vez y cuantas fechas se saltearon porque ya tenian un horario a esa hora
    public record AddedSlots(List<Slot> created, int skipped) {
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
        return validateStartTime(activity, form.getDate().atTime(form.getTime()), current, true);
    }

    // Futura y dentro del limite de meses. checkDuplicate: si ya hay otro horario activo a esa hora es un error
    // (al repetir no, porque esa fecha se saltea)
    private LocalDateTime validateStartTime(Activity activity, LocalDateTime startTime, Slot current, boolean checkDuplicate) {
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new FieldValidationException(Map.of("date", "La fecha y hora tienen que ser futuras."));
        }
        if (startTime.toLocalDate().isAfter(lastAllowedDate())) {
            throw new FieldValidationException(Map.of("date", "Podés cargar horarios hasta " + SlotForm.MAX_MONTHS_AHEAD
                    + " meses adelante (hasta el " + lastAllowedDate().format(DATE) + ")."));
        }
        if (checkDuplicate && activity.hasActiveSlotAt(startTime, current)) {
            throw new FieldValidationException(Map.of("time", "Ya hay un horario de esta actividad en esa fecha y hora."));
        }
        return startTime;
    }
}
