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
import uy.edu.um.luminalabs.dto.CardForm;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.events.RefundRequestedEvent;
import uy.edu.um.luminalabs.events.ReservationConfirmedEvent;
import uy.edu.um.luminalabs.events.ReservationExpiredEvent;
import uy.edu.um.luminalabs.exceptions.PaymentNotPossibleException;
import uy.edu.um.luminalabs.exceptions.ReservationNotFoundException;
import uy.edu.um.luminalabs.payments.ChargeRequest;
import uy.edu.um.luminalabs.payments.GatewayResult;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentCard;
import uy.edu.um.luminalabs.payments.PaymentGateway;
import uy.edu.um.luminalabs.repositories.PaymentRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.services.PaymentService.Result;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// CU-01 pasos 7 a 10 y CU-16 (RF-17, RF-18)
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Long TOURIST_ID = 3L;
    private static final Long RESERVATION_ID = 9L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentService service;

    private Slot slot;
    private Reservation reservation;
    private CardForm card;
    // Hace de tabla "payments": lo que el servicio guarda, despues lo encuentra
    private final List<Payment> storedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        // El TransactionTemplate real, con un administrador de transacciones simulado: ejecuta el codigo y listo
        service = new PaymentService(reservationRepository, paymentRepository, gateway,
                new TransactionTemplate(transactionManager), eventPublisher, clock, "UYU", 3000);

        Tourist tourist = Tourist.builder().firstName("Ana").email("ana@mail.com").build();
        tourist.setId(TOURIST_ID);
        Activity activity = new Activity();
        activity.setName("Kayak");
        activity.setAddress("Parador La Balconada");
        activity.setLocation(new Location("La Paloma", Department.ROCHA));
        activity.setBookingCutoffMinutes(60);
        slot = new Slot(activity, NOW.plusDays(10), new BigDecimal("1500.00"), 10);
        reservation = new Reservation(tourist, slot,
                List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1)),
                        new Participant("Juan", "Pérez", LocalDate.of(1990, 1, 1))), NOW.minusMinutes(30));
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        card = new CardForm();
        card.setCardHolder("Ana Pérez");
        card.setCardNumber("4111 1111 1111 1111");
        card.setExpiry("12/30");
        card.setCvv("123");

        lenient().when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", (long) storedPayments.size() + 100);
            storedPayments.add(payment);
            return payment;
        });
        lenient().when(paymentRepository.findById(anyLong())).thenAnswer(invocation -> storedPayments.stream()
                .filter(payment -> payment.getId().equals(invocation.getArgument(0))).findFirst());
        lenient().when(paymentRepository.findByReservation_IdAndTypeAndStatus(RESERVATION_ID, PaymentType.CHARGE, PaymentStatus.NO_RESPONSE))
                .thenAnswer(invocation -> storedPayments.stream().filter(Payment::isAwaitingResponse).toList());
    }

    private void gatewayCharges(Outcome outcome) {
        when(gateway.charge(any())).thenReturn(new GatewayResult(outcome, outcome == Outcome.APPROVED ? "gw-1" : null));
    }

    // Un intento anterior que quedo sin respuesta hace "secondsAgo" segundos
    private Payment previousAttemptWithoutResponse(long secondsAgo) {
        Payment payment = paymentRepository.save(new Payment(reservation, PaymentType.CHARGE, reservation.getTotalAmount(),
                "Ana Pérez", "3220", NOW.minusSeconds(secondsAgo)));
        clearInvocations(paymentRepository);
        return payment;
    }

    @Test
    void approvedPaymentConfirmsReservation() {
        gatewayCharges(Outcome.APPROVED);

        assertEquals(Result.CONFIRMED, service.pay(TOURIST_ID, RESERVATION_ID, card));

        Payment payment = storedPayments.getFirst();
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals("gw-1", payment.getExternalId());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verify(eventPublisher).publishEvent(any(ReservationConfirmedEvent.class));
    }

    // RF-17: se cobra el total de la reserva; de la tarjeta solo se guardan titular y ultimos 4 digitos
    @Test
    void chargesReservationTotalAndStoresOnlyLastDigits() {
        gatewayCharges(Outcome.APPROVED);

        service.pay(TOURIST_ID, RESERVATION_ID, card);

        ArgumentCaptor<ChargeRequest> request = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(request.capture());
        assertEquals(new BigDecimal("3000.00"), request.getValue().amount());
        assertEquals("4111111111111111", ((PaymentCard.CardDetails) request.getValue().card()).number());
        assertEquals(storedPayments.getFirst().getReference(), request.getValue().reference());
        assertEquals("1111", storedPayments.getFirst().getCardLastDigits());
    }

    // 9a: rechazado o pasarela caida: el intento queda rechazado y la reserva sigue pendiente
    @Test
    void declinedOrUnavailableLeavesReservationPending() {
        gatewayCharges(Outcome.DECLINED);
        assertEquals(Result.REJECTED, service.pay(TOURIST_ID, RESERVATION_ID, card));

        gatewayCharges(Outcome.UNAVAILABLE);
        assertEquals(Result.REJECTED, service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(List.of(PaymentStatus.REJECTED, PaymentStatus.REJECTED), storedPayments.stream().map(Payment::getStatus).toList());
        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.getStatus());
        verifyNoInteractions(eventPublisher);
    }

    // 9b: timeout. El intento queda "sin respuesta": NUNCA se confirma la reserva sin pago aprobado
    @Test
    void timeoutLeavesPaymentWithoutResponse() {
        gatewayCharges(Outcome.NO_RESPONSE);

        assertEquals(Result.NO_RESPONSE, service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(PaymentStatus.NO_RESPONSE, storedPayments.getFirst().getStatus());
        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.getStatus());
    }

    // 5a: el intento sin respuesta se habia cobrado: se confirma SIN cobrar de nuevo
    @Test
    void previousApprovedAttemptConfirmsWithoutChargingAgain() {
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-old"));

        assertEquals(Result.CONFIRMED, service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(PaymentStatus.APPROVED, previous.getStatus());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verify(gateway, never()).charge(any());
        assertEquals(1, storedPayments.size(), "no se registro un intento nuevo");
    }

    // 5a: el intento anterior nunca llego a la pasarela: se marca rechazado y se cobra de nuevo
    @Test
    void previousAttemptNeverReceivedIsRejectedAndChargedAgain() {
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(GatewayResult.of(Outcome.NOT_FOUND));
        gatewayCharges(Outcome.APPROVED);

        assertEquals(Result.CONFIRMED, service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(PaymentStatus.REJECTED, previous.getStatus());
        assertEquals(PaymentStatus.APPROVED, storedPayments.getLast().getStatus());
    }

    // Si no se puede saber que paso con el intento anterior, no se cobra de nuevo (podria cobrar dos veces)
    @Test
    void doesNotChargeWhilePreviousAttemptIsUnknown() {
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(GatewayResult.of(Outcome.UNAVAILABLE));

        assertThrows(PaymentNotPossibleException.class, () -> service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(PaymentStatus.NO_RESPONSE, previous.getStatus());
        verify(gateway, never()).charge(any());
    }

    // Un intento de hace 2 segundos puede estar todavia en camino: ni se consulta ni se cobra otro
    @Test
    void doesNotChargeWhilePreviousAttemptMayStillBeInFlight() {
        previousAttemptWithoutResponse(2);

        assertThrows(PaymentNotPossibleException.class, () -> service.pay(TOURIST_ID, RESERVATION_ID, card));

        verify(gateway, never()).findByReference(any());
        verify(gateway, never()).charge(any());
    }

    // 4a: vencido el plazo de 2 horas no se puede pagar
    @Test
    void cannotPayAfterPaymentDeadline() {
        ReflectionTestUtils.setField(reservation, "createdAt", NOW.minusHours(3));

        assertThrows(PaymentNotPossibleException.class, () -> service.pay(TOURIST_ID, RESERVATION_ID, card));
        verify(gateway, never()).charge(any());
    }

    // 4b: el slot ya no admite reservas: la reserva vence y libera sus lugares
    @Test
    void reservationExpiresIfSlotIsOutsideBookingWindow() {
        ReflectionTestUtils.setField(slot, "startTime", NOW.plusMinutes(30));
        assertEquals(8, slot.getAvailability());

        assertThrows(PaymentNotPossibleException.class, () -> service.pay(TOURIST_ID, RESERVATION_ID, card));

        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertEquals(10, slot.getAvailability());
        verify(gateway, never()).charge(any());
    }

    @Test
    void confirmedReservationCannotBePaidAgain() {
        reservation.confirm();

        assertThrows(PaymentNotPossibleException.class, () -> service.pay(TOURIST_ID, RESERVATION_ID, card));
        verify(gateway, never()).charge(any());
    }

    // RNF-10
    @Test
    void cannotPaySomeoneElsesReservation() {
        assertThrows(ReservationNotFoundException.class, () -> service.pay(99L, RESERVATION_ID, card));
    }

    // "Verificar el pago": confirma si la pasarela dice que se cobro
    @Test
    void checkConfirmsWhenGatewayApprovedThePendingAttempt() {
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-old"));

        assertEquals(Result.CONFIRMED, service.checkPendingPayment(TOURIST_ID, RESERVATION_ID));
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    // ---------- Proceso automatico (CU-18) ----------

    private void paymentDeadlinePassed() {
        ReflectionTestUtils.setField(reservation, "createdAt", NOW.minusHours(3));
    }

    // Paso 2: sin pagos sin respuesta, vence y libera los lugares
    @Test
    void unpaidReservationExpiresAndReleasesSeats() {
        paymentDeadlinePassed();

        assertEquals(ReservationStatus.EXPIRED, service.expireIfUnpaid(RESERVATION_ID));

        assertEquals(10, slot.getAvailability());
        verify(eventPublisher).publishEvent(any(ReservationExpiredEvent.class));
    }

    // 2a: tenia un pago sin respuesta que la pasarela aprobo: se confirma en vez de vencer
    @Test
    void reservationWithLateApprovedPaymentIsConfirmedInsteadOfExpired() {
        paymentDeadlinePassed();
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-old"));

        assertEquals(ReservationStatus.CONFIRMED, service.expireIfUnpaid(RESERVATION_ID));
        assertEquals(8, slot.getAvailability());
    }

    // 2b: si no se puede consultar a la pasarela, queda pendiente (podria estar paga) y no se liberan los lugares
    @Test
    void reservationStaysPendingWhileGatewayCannotBeQueried() {
        paymentDeadlinePassed();
        Payment previous = previousAttemptWithoutResponse(60);
        when(gateway.findByReference(previous.getReference())).thenReturn(GatewayResult.of(Outcome.UNAVAILABLE));

        assertEquals(ReservationStatus.PENDING_PAYMENT, service.expireIfUnpaid(RESERVATION_ID));
        assertEquals(8, slot.getAvailability());
    }

    @Test
    void reservationWithinPaymentWindowDoesNotExpire() {
        assertEquals(ReservationStatus.PENDING_PAYMENT, service.expireIfUnpaid(RESERVATION_ID));
        verifyNoInteractions(gateway);
    }

    // La plataforma cancelo la reserva mientras un cobro estaba sin respuesta, y la pasarela lo aprobo:
    // el turista pago algo que ya no tiene, asi que se le devuelve todo
    @Test
    void lateApprovedChargeOfCancelledReservationIsRefunded() {
        Payment charge = previousAttemptWithoutResponse(60);
        reservation.cancel();
        when(paymentRepository.findByIdForUpdate(charge.getId())).thenReturn(Optional.of(charge));
        when(gateway.findByReference(charge.getReference())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-late"));

        service.resolveOrphanCharge(charge.getId());

        assertEquals(PaymentStatus.APPROVED, charge.getStatus());
        Payment refund = storedPayments.getLast();
        assertEquals(PaymentType.REFUND, refund.getType());
        assertEquals(charge.getAmount(), refund.getAmount());
        verify(eventPublisher).publishEvent(new RefundRequestedEvent(refund.getId()));
    }
}
