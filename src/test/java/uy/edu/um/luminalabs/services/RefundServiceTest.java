package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.payments.GatewayResult;
import uy.edu.um.luminalabs.payments.GatewayResult.Outcome;
import uy.edu.um.luminalabs.payments.PaymentGateway;
import uy.edu.um.luminalabs.payments.RefundRequest;
import uy.edu.um.luminalabs.repositories.PaymentRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// RF-19 y CU-05 8a: devoluciones y sus reintentos
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private PlatformTransactionManager transactionManager;

    private RefundService service;
    private Payment refund;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new RefundService(paymentRepository, gateway, new TransactionTemplate(transactionManager), clock, "UYU");

        Slot slot = new Slot(new Activity(), NOW.plusDays(3), BigDecimal.TEN, 5);
        Reservation reservation = new Reservation(new Tourist(), slot,
                List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1))), NOW);
        ReflectionTestUtils.setField(reservation, "id", 9L);
        Payment charge = new Payment(reservation, PaymentType.CHARGE, BigDecimal.TEN, "Ana", "1111", NOW);
        charge.approve("gw-charge", NOW);
        refund = Payment.refundOf(charge, new BigDecimal("8.50"), NOW);
        ReflectionTestUtils.setField(refund, "id", 50L);

        lenient().when(paymentRepository.findById(50L)).thenReturn(Optional.of(refund));
        lenient().when(paymentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(refund));
        lenient().when(paymentRepository.findByReservation_IdAndTypeAndStatus(9L, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(List.of(charge));
    }

    @Test
    void approvedRefund() {
        when(gateway.refund(any())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-refund"));

        assertEquals(PaymentStatus.APPROVED, service.processRefund(50L));

        // Se pide con la referencia de la devolucion y el id del cobro original en la pasarela
        verify(gateway).refund(new RefundRequest("REF-50", "gw-charge", new BigDecimal("8.50"), "UYU"));
        assertEquals("gw-refund", refund.getExternalId());
    }

    // 8a: si falla, queda pendiente para reintentarla
    @Test
    void failedRefundStaysPending() {
        when(gateway.refund(any())).thenReturn(GatewayResult.of(Outcome.UNAVAILABLE));

        assertEquals(PaymentStatus.PENDING, service.processRefund(50L));
    }

    @Test
    void timedOutRefundStaysPending() {
        when(gateway.refund(any())).thenReturn(GatewayResult.of(Outcome.NO_RESPONSE));

        assertEquals(PaymentStatus.PENDING, service.processRefund(50L));
    }

    // Una devolucion ya aprobada no se vuelve a pedir
    @Test
    void approvedRefundIsNotRequestedAgain() {
        refund.approve("gw-refund", NOW);

        assertEquals(PaymentStatus.APPROVED, service.processRefund(50L));
        verifyNoInteractions(gateway);
    }

    // CU-18 paso 4
    @Test
    void retriesPendingRefunds() {
        when(paymentRepository.findByTypeAndStatusAndCreatedAtBefore(eq(PaymentType.REFUND), eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(refund));
        when(gateway.refund(any())).thenReturn(new GatewayResult(Outcome.APPROVED, "gw-refund"));

        assertEquals(1, service.retryPendingRefunds());
        assertEquals(PaymentStatus.APPROVED, refund.getStatus());
    }
}
