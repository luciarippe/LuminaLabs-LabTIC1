package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import uy.edu.um.luminalabs.dto.SlotForm;
import uy.edu.um.luminalabs.dto.SlotRepetition;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.SlotRescheduledEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.exceptions.SlotNotFoundException;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// CU-04: gestionar los slots de una actividad
@ExtendWith(MockitoExtension.class)
class ProviderSlotServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long PROVIDER_ID = 7L;
    private static final Long ACTIVITY_ID = 5L;

    @Mock
    private ProviderActivityService activityService;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CancellationService cancellationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProviderSlotService service;

    private Business business;
    private Activity activity;
    private SlotForm form;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new ProviderSlotService(activityService, slotRepository, reservationRepository,
                cancellationService, eventPublisher, clock);

        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        business = new Business();
        business.addProvider(provider);
        business.approve(NOW);
        activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setBusiness(business);

        form = new SlotForm();
        form.setDate(LocalDate.of(2026, 11, 1));
        form.setTime(LocalTime.of(10, 0));
        form.setPrice(BigDecimal.valueOf(1500));
        form.setMaxCapacity(12);
    }

    private Slot existingSlot(LocalDateTime start) {
        Slot slot = activity.addSlot(new Slot(activity, start, BigDecimal.valueOf(1500), 12));
        ReflectionTestUtils.setField(slot, "id", 30L);
        when(slotRepository.findWithActivityById(30L)).thenReturn(Optional.of(slot));
        return slot;
    }

    @Test
    void addsActiveSlotWithFullAvailability() {
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);
        when(slotRepository.save(any(Slot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Slot slot = service.addSlot(PROVIDER_ID, ACTIVITY_ID, form);

        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), slot.getStartTime());
        assertEquals(12, slot.getAvailability());
        assertEquals(SlotStatus.ACTIVE, slot.getStatus());
        assertTrue(activity.getSlots().contains(slot));
    }

    @Test
    void rejectsPastDate() {
        form.setDate(NOW.toLocalDate());
        form.setTime(NOW.toLocalTime().minusHours(1));
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.addSlot(PROVIDER_ID, ACTIVITY_ID, form));

        assertTrue(exception.getFieldErrors().containsKey("date"));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicatedStartTime() {
        activity.addSlot(new Slot(activity, LocalDateTime.of(2026, 11, 1, 10, 0), BigDecimal.TEN, 5));
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.addSlot(PROVIDER_ID, ACTIVITY_ID, form));

        assertTrue(exception.getFieldErrors().containsKey("time"));
    }

    // Opcion 1: un slot cancelado no impide crear otro a la misma hora
    @Test
    void cancelledSlotDoesNotBlockSameStartTime() {
        Slot cancelled = activity.addSlot(new Slot(activity, LocalDateTime.of(2026, 11, 1, 10, 0), BigDecimal.TEN, 5));
        cancelled.cancel(NOW);
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);
        when(slotRepository.save(any(Slot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Slot slot = service.addSlot(PROVIDER_ID, ACTIVITY_ID, form);

        assertEquals(cancelled.getStartTime(), slot.getStartTime());
        assertTrue(slot.isActive());
    }

    // RF-06: un emprendimiento suspendido no puede publicar slots
    @Test
    void suspendedBusinessCannotAddSlots() {
        business.suspend("Motivo", NOW);
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);

        assertThrows(IllegalStateException.class, () -> service.addSlot(PROVIDER_ID, ACTIVITY_ID, form));
    }

    @Test
    void updatingSameSlotKeepingItsTimeIsNotADuplicate() {
        Slot slot = existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));
        form.setMaxCapacity(20);

        service.updateSlot(PROVIDER_ID, ACTIVITY_ID, 30L, form);

        assertEquals(20, slot.getMaxCapacity());
    }

    @Test
    void capacityBelowReservedSeatsIsAFieldError() {
        Slot slot = existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));
        slot.reserveSeats(10);   // quedan 2 de 12
        form.setMaxCapacity(8);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.updateSlot(PROVIDER_ID, ACTIVITY_ID, 30L, form));

        assertTrue(exception.getFieldErrors().containsKey("maxCapacity"));
        assertEquals(12, slot.getMaxCapacity());
    }

    @Test
    void cancelsSlot() {
        Slot slot = existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));

        service.cancelSlot(PROVIDER_ID, ACTIVITY_ID, 30L);

        assertEquals(SlotStatus.CANCELLED, slot.getStatus());
    }

    private Reservation reservationOn(Slot slot) {
        Tourist tourist = Tourist.builder().firstName("Ana").email("ana@mail.com").build();
        return new Reservation(tourist, slot, List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1))), NOW);
    }

    // 5a: cambio de horario con reservas: se marcan (devolucion total, CU-05 4b) y se avisa a cada turista
    @Test
    void scheduleChangeMarksReservationsAndNotifiesTourists() {
        Slot slot = existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));
        Reservation reservation = reservationOn(slot);
        when(reservationRepository.findBySlot_IdAndStatusIn(eq(30L), any())).thenReturn(List.of(reservation));
        form.setTime(LocalTime.of(12, 0));

        service.updateSlot(PROVIDER_ID, ACTIVITY_ID, 30L, form);

        assertTrue(reservation.isScheduleChanged());
        verify(eventPublisher).publishEvent(any(SlotRescheduledEvent.class));
    }

    @Test
    void priceChangeDoesNotMarkOrNotify() {
        existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));
        form.setPrice(BigDecimal.valueOf(2000));

        service.updateSlot(PROVIDER_ID, ACTIVITY_ID, 30L, form);

        verifyNoInteractions(reservationRepository, eventPublisher);
    }

    // 5b: las reservas del slot se cancelan con devolucion total
    @Test
    void cancellingSlotCancelsItsReservationsWithFullRefund() {
        Slot slot = existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));
        List<Reservation> reservations = List.of(reservationOn(slot));
        when(reservationRepository.findBySlot_IdAndStatusIn(eq(30L), any())).thenReturn(reservations);
        when(cancellationService.cancelWithFullRefund(reservations, CancellationReason.SLOT_CANCELLED)).thenReturn(1);

        assertEquals(1, service.cancelSlot(PROVIDER_ID, ACTIVITY_ID, 30L));
    }

    // RNF-10: no se puede tocar un slot de otro prestador, ni pasarlo por la URL de otra actividad
    @Test
    void slotOfOtherProviderOrActivityIsNotFound() {
        existingSlot(LocalDateTime.of(2026, 11, 1, 10, 0));

        assertThrows(SlotNotFoundException.class, () -> service.cancelSlot(99L, ACTIVITY_ID, 30L));
        assertThrows(SlotNotFoundException.class, () -> service.cancelSlot(PROVIDER_ID, 6L, 30L));
    }

    // ---------- Aviso de horarios por terminarse ----------

    private Activity activityOf(Business owner, long id) {
        Activity other = new Activity();
        other.setId(id);
        other.setBusiness(owner);
        return other;
    }

    // Lo que devuelve la consulta "summarize" para una actividad con horarios hasta "lastStart"
    private static SlotRepository.SlotSummary summary(long activityId, LocalDateTime lastStart) {
        return new SlotRepository.SlotSummary() {
            public Long getActivityId() { return activityId; }
            public BigDecimal getFromPrice() { return BigDecimal.TEN; }
            public LocalDateTime getNextStart() { return lastStart; }
            public LocalDateTime getLastStart() { return lastStart; }
        };
    }

    @Test
    void warnsAboutActivitiesWithoutSlotsOrEndingWithinThirtyDays() {
        Activity endsSoon = activityOf(business, 1L);
        Activity endsLater = activityOf(business, 2L);
        Activity noSlots = activityOf(business, 3L);
        // Hoy es 17/10/2026
        when(slotRepository.summarize(anyList(), eq(SlotStatus.ACTIVE), any())).thenReturn(List.of(
                summary(1L, LocalDateTime.of(2026, 10, 27, 10, 0)),
                summary(2L, LocalDateTime.of(2026, 12, 20, 10, 0))));

        List<ProviderSlotService.RunningOut> runningOut = service.findRunningOut(List.of(endsSoon, endsLater, noSlots));

        // Primero la que no tiene horarios; la que termina en diciembre no se avisa
        assertEquals(List.of(noSlots, endsSoon), runningOut.stream().map(ProviderSlotService.RunningOut::activity).toList());
        assertTrue(runningOut.get(0).hasNoSlots());
        assertEquals(10, runningOut.get(1).daysLeft());
    }

    @Test
    void ignoresActivitiesOfBusinessesThatCannotPublish() {
        Business pending = new Business();
        Activity ofPending = activityOf(pending, 4L);

        assertTrue(service.findRunningOut(List.of(ofPending)).isEmpty());
        verify(slotRepository, never()).summarize(anyList(), any(), any());
    }

    // ---------- Repetir un horario ----------

    private void repeatable() {
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);
        lenient().when(slotRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static List<LocalDate> dates(ProviderSlotService.AddedSlots added) {
        return added.created().stream().map(slot -> slot.getStartTime().toLocalDate()).toList();
    }

    @Test
    void repeatsWeeklyUntilTheChosenDate() {
        repeatable();
        // 01/11/2026 es domingo
        form.setRepetition(SlotRepetition.WEEKLY);
        form.setRepeatUntil(LocalDate.of(2026, 11, 29));

        ProviderSlotService.AddedSlots added = service.addSlots(PROVIDER_ID, ACTIVITY_ID, form);

        assertEquals(List.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 29)), dates(added));
        assertTrue(added.created().stream().allMatch(slot -> slot.getStartTime().toLocalTime().equals(LocalTime.of(10, 0))
                && slot.getAvailability() == 12));
        assertEquals(0, added.skipped());
    }

    @Test
    void repeatsOnChosenWeekdaysOnly() {
        repeatable();
        form.setRepetition(SlotRepetition.WEEKDAYS);
        form.setWeekdays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        form.setRepeatUntil(LocalDate.of(2026, 11, 14));

        ProviderSlotService.AddedSlots added = service.addSlots(PROVIDER_ID, ACTIVITY_ID, form);

        assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13)), dates(added));
    }

    @Test
    void skipsDatesThatAlreadyHaveASlotAtThatTime() {
        repeatable();
        activity.addSlot(new Slot(activity, LocalDateTime.of(2026, 11, 8, 10, 0), BigDecimal.TEN, 5));
        form.setRepetition(SlotRepetition.WEEKLY);
        form.setRepeatUntil(LocalDate.of(2026, 11, 29));

        ProviderSlotService.AddedSlots added = service.addSlots(PROVIDER_ID, ACTIVITY_ID, form);

        assertEquals(4, added.created().size());
        assertFalse(dates(added).contains(LocalDate.of(2026, 11, 8)));
        assertEquals(1, added.skipped());
    }

    @Test
    void rejectsRepetitionBeyondSixMonths() {
        repeatable();
        // Hoy es 17/10/2026: se puede cargar hasta el 17/04/2027
        form.setRepetition(SlotRepetition.DAILY);
        form.setRepeatUntil(LocalDate.of(2027, 5, 1));

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.addSlots(PROVIDER_ID, ACTIVITY_ID, form));

        assertTrue(exception.getFieldErrors().get("repeatUntil").contains("17/04/2027"));
        verify(slotRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsSingleSlotBeyondSixMonths() {
        when(activityService.findOwnActivity(PROVIDER_ID, ACTIVITY_ID)).thenReturn(activity);
        form.setDate(LocalDate.of(2027, 5, 1));

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.addSlot(PROVIDER_ID, ACTIVITY_ID, form));

        assertTrue(exception.getFieldErrors().containsKey("date"));
    }

    @Test
    void repetitionNeedsAnEndDate() {
        repeatable();
        form.setRepetition(SlotRepetition.EVERY_OTHER_DAY);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.addSlots(PROVIDER_ID, ACTIVITY_ID, form));

        assertTrue(exception.getFieldErrors().containsKey("repeatUntil"));
    }
}
