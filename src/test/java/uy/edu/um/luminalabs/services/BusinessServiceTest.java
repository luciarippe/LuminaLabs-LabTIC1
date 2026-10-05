package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent.Change;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.repositories.BusinessRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BusinessService service;

    private Business pendingBusinessWithProvider() {
        Provider provider = Provider.builder().firstName("Ana").email("ana@mail.com").build();
        Business business = new Business();
        business.setLegalName("Cabalgatas del Este");
        business.addProvider(provider);
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        return business;
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
        business.approve();
        business.suspend("Motivo");

        service.reactivate(1L);

        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof BusinessStatusChangedEvent e && e.change() == Change.REACTIVATED));
    }

    @Test
    void invalidTransitionSendsNoEmail() {
        pendingBusinessWithProvider();

        assertThrows(IllegalStateException.class, () -> service.suspend(1L, "Motivo"));

        verifyNoInteractions(eventPublisher);
    }
}
