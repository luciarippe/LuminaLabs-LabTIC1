package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.CancellationReason;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent.Change;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CancellationService cancellationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BusinessService service;

    @BeforeEach
    void setUp() {
        // Reloj fijo: la fecha de revision es siempre NOW
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new BusinessService(businessRepository, reservationRepository, cancellationService, eventPublisher, clock);
    }

    private Business pendingBusinessWithProvider() {
        Provider provider = Provider.builder().firstName("Ana").email("ana@mail.com").build();
        Business business = new Business();
        business.setLegalName("Cabalgatas del Este");
        business.addProvider(provider);
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        return business;
    }

    @Test
    void approveUsesApplicationClock() {
        Business business = pendingBusinessWithProvider();

        service.approve(1L);

        assertEquals(NOW, business.getReviewedAt());
    }

    @Test
    void denyNotifiesProvidersWithReason() {
        pendingBusinessWithProvider();

        service.deny(1L, "Faltan datos bancarios");

        verify(eventPublisher).publishEvent(new BusinessStatusChangedEvent(
                List.of(new MailRecipient("ana@mail.com", "Ana")), "Cabalgatas del Este",
                Change.DENIED, "Faltan datos bancarios"));
    }

    @Test
    void reactivationIsNotifiedDifferentlyFromApproval() {
        Business business = pendingBusinessWithProvider();
        business.approve(NOW);
        business.suspend("Motivo", NOW);

        service.reactivate(1L);

        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof BusinessStatusChangedEvent e && e.change() == Change.REACTIVATED));
    }

    // RF-06 y CU-11 paso 8: al suspender, sus reservas vigentes se cancelan con devolucion total
    @Test
    void suspendingCancelsActiveReservationsWithFullRefund() {
        Business business = pendingBusinessWithProvider();
        business.approve(NOW);
        List<Reservation> reservations = List.of(mock(Reservation.class));
        when(reservationRepository.findBySlot_Activity_Business_IdAndStatusInAndSlot_StartTimeAfter(eq(1L), any(), eq(NOW)))
                .thenReturn(reservations);

        service.suspend(1L, "Denuncias");

        verify(cancellationService).cancelWithFullRefund(reservations, CancellationReason.BUSINESS_SUSPENDED);
    }

    @Test
    void invalidTransitionSendsNoEmail() {
        pendingBusinessWithProvider();

        assertThrows(IllegalStateException.class, () -> service.suspend(1L, "Motivo"));

        verifyNoInteractions(eventPublisher);
    }
}
