package uy.edu.um.luminalabs.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uy.edu.um.luminalabs.entities.Bank;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.services.BusinessService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Busqueda de emprendimientos del administrador contra una base de verdad (H2 en modo PostgreSQL),
// para probar la consulta JPQL (LIKE, escape de comodines). Ver SlotPersistenceTest por la configuracion.
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BusinessSearchPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private BusinessRepository businessRepository;

    private BusinessService service;

    @BeforeEach
    void setUp() {
        // search solo usa el repositorio
        service = new BusinessService(businessRepository, null, null, null, null);
        business("Cabalgatas del Este", BusinessType.COMPANY, "211234560019", 1);
        business("Kayak 100% Rocha", BusinessType.INDIVIDUAL, "45678901", 2);
        business("Sierras de Minas", BusinessType.INDIVIDUAL, "1234567", 3);
        entityManager.flush();
    }

    private Business business(String name, BusinessType type, String identifier, int day) {
        Business business = new Business();
        business.setType(type);
        business.setLegalName(name);
        business.setIdentifier(identifier);
        business.setBank(Bank.BROU);
        business.setBankAccountNumber("00123456700001");
        business.setDescription("Descripción");
        business.setContactEmail("a@b.uy");
        business.setContactPhone("099 000 000");
        business.setCreatedAt(LocalDateTime.of(2026, 10, day, 12, 0));
        return entityManager.persist(business);
    }

    private List<String> deniedNames(String query) {
        return service.findByStatusLatestFirst(BusinessStatus.DENIED, query).stream().map(Business::getLegalName).toList();
    }

    // Seccion "Rechazados": solo los denegados, el rechazo mas reciente primero, y se puede buscar entre ellos
    @Test
    void listsOnlyDeniedBusinessesNewestRejectionFirst() {
        business("Bodega Rechazada", BusinessType.COMPANY, "219999990001", 4).deny("Falta el RUT", LocalDateTime.of(2026, 10, 10, 9, 0));
        business("Kayak Rechazado", BusinessType.INDIVIDUAL, "45678902", 5).deny("Datos bancarios incorrectos", LocalDateTime.of(2026, 10, 12, 9, 0));
        entityManager.flush();

        assertEquals(List.of("Kayak Rechazado", "Bodega Rechazada"), deniedNames(""));
        // "kayak" tambien encuentra "Kayak 100% Rocha", pero ese no esta rechazado
        assertEquals(List.of("Kayak Rechazado"), deniedNames("kayak"));
        assertEquals(List.of("Bodega Rechazada"), deniedNames("2199999900"));
    }

    // Seccion "Suspendidos": misma consulta con otro estado; un rechazado no aparece
    @Test
    void listsSuspendedBusinessesSeparatelyFromDenied() {
        Business suspended = business("Surf Suspendido", BusinessType.COMPANY, "219999990002", 4);
        suspended.approve(LocalDateTime.of(2026, 10, 5, 9, 0));
        suspended.suspend("Reclamos de turistas", LocalDateTime.of(2026, 10, 15, 9, 0));
        business("Bodega Rechazada", BusinessType.COMPANY, "219999990001", 5).deny("Falta el RUT", LocalDateTime.of(2026, 10, 10, 9, 0));
        entityManager.flush();

        assertEquals(List.of("Surf Suspendido"), service.findByStatusLatestFirst(BusinessStatus.SUSPENDED, "")
                .stream().map(Business::getLegalName).toList());
        assertTrue(service.findByStatusLatestFirst(BusinessStatus.SUSPENDED, "bodega").isEmpty());
    }

    private List<String> names(String query) {
        return service.search(query).stream().map(Business::getLegalName).toList();
    }

    @Test
    void findsByPartOfTheNameIgnoringCase() {
        assertEquals(List.of("Cabalgatas del Este"), names("  CABALGATAS "));
    }

    @Test
    void findsByPartOfTheIdentifierEvenWithSeparators() {
        assertEquals(List.of("Cabalgatas del Este"), names("21.123456"));
        // 123456 aparece dentro de dos identificadores; el mas nuevo primero
        assertEquals(List.of("Sierras de Minas", "Cabalgatas del Este"), names("123456"));
    }

    @Test
    void percentSignIsSearchedAsText() {
        assertEquals(List.of("Kayak 100% Rocha"), names("100%"));
        assertTrue(names("%").contains("Kayak 100% Rocha"));
        assertEquals(1, names("%").size());
    }

    @Test
    void returnsNothingWhenNothingMatches() {
        assertTrue(names("Tacuarembó").isEmpty());
    }
}
