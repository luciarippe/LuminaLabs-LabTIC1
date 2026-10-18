package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Test
    void newBusinessIsPending() {
        assertEquals(BusinessStatus.PENDING, new Business().getStatus());
    }

    @Test
    void approvePendingBusiness() {
        Business business = new Business();

        business.approve(NOW);

        assertEquals(BusinessStatus.APPROVED, business.getStatus());
        assertEquals(NOW, business.getReviewedAt());
    }

    @Test
    void denyStoresReason() {
        Business business = new Business();

        business.deny("Datos bancarios incompletos", NOW);

        assertEquals(BusinessStatus.DENIED, business.getStatus());
        assertEquals("Datos bancarios incompletos", business.getStatusReason());
    }

    @Test
    void cannotApproveDeniedBusiness() {
        Business business = new Business();
        business.deny("Motivo", NOW);

        assertThrows(IllegalStateException.class, () -> business.approve(NOW));
    }

    @Test
    void cannotSuspendPendingBusiness() {
        assertThrows(IllegalStateException.class, () -> new Business().suspend("Motivo", NOW));
    }

    @Test
    void suspendAndReactivateApprovedBusiness() {
        Business business = new Business();
        business.approve(NOW);

        business.suspend("Denuncias de turistas", NOW);
        assertEquals(BusinessStatus.SUSPENDED, business.getStatus());
        assertEquals("Denuncias de turistas", business.getStatusReason());

        business.reactivate(NOW);
        assertEquals(BusinessStatus.APPROVED, business.getStatus());
        assertNull(business.getStatusReason());
    }

    // CU-02 5c
    @Test
    void deniedBusinessCanBeResubmitted() {
        Business business = new Business();
        business.deny("Falta el RUT", NOW);

        business.resubmit();

        assertEquals(BusinessStatus.PENDING, business.getStatus());
        assertNull(business.getStatusReason());
        assertNull(business.getReviewedAt());
    }

    @Test
    void onlyDeniedBusinessCanBeResubmitted() {
        Business business = new Business();
        business.approve(NOW);

        assertThrows(IllegalStateException.class, business::resubmit);
    }

    @Test
    void providerCanLogInOnlyWithApprovedBusiness() {
        Provider provider = new Provider();
        Business business = new Business();
        business.addProvider(provider);
        assertFalse(provider.hasApprovedBusiness());

        business.approve(NOW);

        assertTrue(provider.hasApprovedBusiness());
    }
}
