package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.RefundRequestedEvent;
import uy.edu.um.luminalabs.events.ReservationCancelledEvent;
import uy.edu.um.luminalabs.repositories.CancellationRepository;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.services.CancellationQuote.Case;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-05 y RF-19: politica de cancelacion, cancelacion y devolucion
@ExtendWith(MockitoExtension.class)
class CancellationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long TOURIST_ID = 3L;
    private static final Long RESERVATION_ID = 9L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CancellationRepository cancellationRepository;
    @Mock
    private RefundService refundService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CancellationService service;

    private Activity activity;
    private Slot slot;
    private Reservation reservation;
    private final List<Payment> storedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new CancellationService(reservationRepository, paymentRepository, cancellationRepository, refundService,
                new TransactionTemplate(transactionManager), eventPublisher, clock);

        Tourist tourist = Tourist.builder().firstName("Ana").email("ana@mail.com").build();
        tourist.setId(TOURIST_ID);
        Business business = new Business();
        activity = new Activity();
        activity.setName("Kayak");
        activity.setBusiness(business);
        activity.setCancellationDeadlineHours(24);
        activity.setCancellationFeePercentage(15);
        // 3 dias antes de la actividad: dentro del plazo de 24 horas
        slot = new Slot(activity, NOW.plusDays(3), new BigDecimal("1333.33"), 10);
        reservation = new Reservation(tourist, slot, List.of(
                new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1)),
                new Participant("Juan", "Pérez", LocalDate.of(1990, 1, 1)),
                new Participant("Luz", "Pérez", LocalDate.of(1990, 1, 1))), NOW.minusDays(1));
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        lenient().when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", (long) storedPayments.size() + 100);
            storedPayments.add(payment);
            return payment;
        });
        lenient().when(paymentRepository.findByReservation_IdAndTypeAndStatus(any(), any(), any())).thenAnswer(invocation ->
                storedPayments.stream()
                        .filter(payment -> payment.getType() == invocation.getArgument(1) && payment.getStatus() == invocation.getArgument(2))
                        .toList());
        lenient().when(cancellationRepository.findByReservation_Id(RESERVATION_ID)).thenReturn(Optional.of(mock(Cancellation.class)));
    }

    // La reserva se pago (cobro aprobado de 3 x 1333,33 = 3999,99)
    private void paid() {
        Payment charge = paymentRepository.save(new Payment(reservation, PaymentType.CHARGE, reservation.getTotalAmount(),
                "Ana Pérez", "1111", NOW.minusDays(1)));
        charge.approve("gw-1", NOW.minusDays(1));
        reservation.confirm();
    }

    // Dentro del plazo: cargo = 15 % de 3999,99 = 599,9985, redondeado a 600,00. Devolucion: 3399,99
    @Test
    void withinDeadlineRefundsTotalMinusFee() {
        paid();

        CancellationQuote quote = service.touristQuote(reservation, NOW);

        assertEquals(Case.WITHIN_DEADLINE, quote.policyCase());
        assertEquals(new BigDecimal("3999.99"), quote.paid());
        assertEquals(new BigDecimal("600.00"), quote.fee());
        assertEquals(new BigDecimal("3399.99"), quote.refund());
    }

    // 4a: a 10 horas del inicio (plazo de 24 horas) no hay devolucion
    @Test
    void outsideDeadlineHasNoRefund() {
        paid();

        CancellationQuote quote = service.touristQuote(reservation, slot.getStartTime().minusHours(10));

        assertEquals(Case.OUTSIDE_DEADLINE, quote.policyCase());
        assertEquals(quote.paid(), quote.fee());
        assertFalse(quote.hasRefund());
    }

    // El limite exacto (justo 24 horas antes) todavia esta dentro del plazo
    @Test
    void deadlineIsInclusive() {
        paid();

        assertEquals(Case.WITHIN_DEADLINE, service.touristQuote(reservation, slot.getStartTime().minusHours(24)).policyCase());
    }

    // 4b: si el prestador cambio el horario, devolucion total aunque este fuera de plazo
    @Test
    void scheduleChangeRefundsEverything() {
        paid();
        reservation.markScheduleChanged();

        CancellationQuote quote = service.touristQuote(reservation, slot.getStartTime().minusHours(1));

        assertEquals(Case.SCHEDULE_CHANGED, quote.policyCase());
        assertEquals(new BigDecimal("3999.99"), quote.refund());
    }

    // 2a: pendiente de pago, no hay nada que devolver
    @Test
    void unpaidReservationHasNothingToRefund() {
        CancellationQuote quote = service.touristQuote(reservation, NOW);

        assertEquals(Case.NOT_PAID, quote.policyCase());
        assertFalse(quote.hasRefund());
    }

    @Test
    void cancelByTouristReleasesSeatsAndRequestsRefund() {
        paid();
        assertEquals(7, slot.getAvailability());

        service.cancelByTourist(TOURIST_ID, RESERVATION_ID);

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(10, slot.getAvailability());
        Payment refund = storedPayments.getLast();
        assertEquals(PaymentType.REFUND, refund.getType());
        assertEquals(PaymentStatus.PENDING, refund.getStatus());
        assertEquals(new BigDecimal("3399.99"), refund.getAmount());
        assertEquals("1111", refund.getCardLastDigits(), "se devuelve a la misma tarjeta");
        verify(refundService).processRefund(refund.getId());

        ArgumentCaptor<Cancellation> cancellation = ArgumentCaptor.forClass(Cancellation.class);
        verify(cancellationRepository).save(cancellation.capture());
        assertEquals(new BigDecimal("600.00"), cancellation.getValue().getFeeAmount());
        assertSame(refund, cancellation.getValue().getRefundPayment());
        verify(eventPublisher).publishEvent(any(ReservationCancelledEvent.class));
    }

    @Test
    void cancellingUnpaidReservationCreatesNoRefund() {
        service.cancelByTourist(TOURIST_ID, RESERVATION_ID);

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertTrue(storedPayments.isEmpty());
        verifyNoInteractions(refundService);
    }

    // Con un cobro sin respuesta no se sabe si pago: primero hay que verificarlo
    @Test
    void cannotCancelWithPaymentAwaitingResponse() {
        paymentRepository.save(new Payment(reservation, PaymentType.CHARGE, reservation.getTotalAmount(), "Ana", "3220", NOW));

        assertThrows(IllegalStateException.class, () -> service.cancelByTourist(TOURIST_ID, RESERVATION_ID));
        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.getStatus());
    }

    @Test
    void cannotCancelAfterActivityStarted() {
        paid();
        ReflectionTestUtils.setField(slot, "startTime", NOW.minusMinutes(5));

        assertThrows(IllegalStateException.class, () -> service.cancelByTourist(TOURIST_ID, RESERVATION_ID));
    }

    // CU-04 5b y RF-06: la plataforma cancela con devolucion total, que se procesa despues de la transaccion
    @Test
    void platformCancellationRefundsEverythingAfterCommit() {
        paid();

        assertEquals(1, service.cancelWithFullRefund(List.of(reservation), CancellationReason.SLOT_CANCELLED));

        Payment refund = storedPayments.getLast();
        assertEquals(new BigDecimal("3999.99"), refund.getAmount());
        verify(eventPublisher).publishEvent(new RefundRequestedEvent(refund.getId()));
        verifyNoInteractions(refundService);
    }
}
