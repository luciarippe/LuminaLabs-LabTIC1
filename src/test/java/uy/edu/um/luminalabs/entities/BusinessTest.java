package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTest {

    @Test
    void newBusinessIsPending() {
        assertEquals(BusinessStatus.PENDING, new Business().getStatus());
    }

    @Test
    void approvePendingBusiness() {
        Business business = new Business();

        business.approve();

        assertEquals(BusinessStatus.APPROVED, business.getStatus());
        assertNotNull(business.getReviewedAt());
    }

    @Test
    void denyStoresReason() {
        Business business = new Business();

        business.deny("Datos bancarios incompletos");

        assertEquals(BusinessStatus.DENIED, business.getStatus());
        assertEquals("Datos bancarios incompletos", business.getStatusReason());
    }

    @Test
    void cannotApproveDeniedBusiness() {
        Business business = new Business();
        business.deny("Motivo");

        assertThrows(IllegalStateException.class, business::approve);
    }

    @Test
    void cannotSuspendPendingBusiness() {
        assertThrows(IllegalStateException.class, () -> new Business().suspend("Motivo"));
    }

    @Test
    void suspendAndReactivateApprovedBusiness() {
        Business business = new Business();
        business.approve();

        business.suspend("Denuncias de turistas");
        assertEquals(BusinessStatus.SUSPENDED, business.getStatus());
        assertEquals("Denuncias de turistas", business.getStatusReason());

        business.reactivate();
        assertEquals(BusinessStatus.APPROVED, business.getStatus());
        assertNull(business.getStatusReason());
    }

    @Test
    void providerCanLogInOnlyWithApprovedBusiness() {
        Provider provider = new Provider();
        Business business = new Business();
        business.addProvider(provider);
        assertFalse(provider.hasApprovedBusiness());

        business.approve();

        assertTrue(provider.hasApprovedBusiness());
    }
}
