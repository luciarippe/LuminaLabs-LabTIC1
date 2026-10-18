package uy.edu.um.luminalabs.repositories;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uy.edu.um.luminalabs.entities.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Prueba contra una base de verdad (H2 en memoria) lo que los tests con mocks no pueden:
// la restriccion unica de los slots y como se guarda una reserva.
// replace = NONE: usa la H2 del perfil "test" (modo PostgreSQL) en vez de la H2 por defecto de @DataJpaTest,
// que se comporta distinto (rechazaba el INSERT de un Tourist).
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SlotPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Autowired
    private TestEntityManager entityManager;

    private Activity activity;

    @BeforeEach
    void setUp() {
        Location location = entityManager.persist(new Location("Minas", Department.LAVALLEJA));

        Business business = new Business();
        business.setType(BusinessType.INDIVIDUAL);
        business.setLegalName("Sierras de Lavalleja");
        business.setIdentifier("12345678");
        business.setBankName("BROU");
        business.setBankAccountNumber("001");
        business.setDescription("Caminatas");
        business.setContactEmail("a@b.uy");
        business.setContactPhone("099 000 000");
        entityManager.persist(business);

        activity = new Activity();
        activity.setBusiness(business);
        activity.setLocation(location);
        activity.setName("Trekking");
        activity.setDescription("Subida al cerro");
        activity.setAddress("Parque Salus");
        activity.setDurationMinutes(120);
        activity.setLanguage("Español");
        entityManager.persist(activity);
    }

    private Slot slotAtStart() {
        return entityManager.persist(new Slot(activity, START, BigDecimal.valueOf(1500), 10));
    }

    // Opcion 1: los cancelados tienen active_start_time NULL y no chocan entre si ni con el activo
    @Test
    void cancelledSlotsDoNotBlockAnActiveOneAtTheSameTime() {
        slotAtStart().cancel(NOW);
        entityManager.flush();
        slotAtStart().cancel(NOW);
        entityManager.flush();

        slotAtStart();

        assertDoesNotThrow(() -> entityManager.flush());
    }

    // Con ids IDENTITY, Hibernate hace el INSERT apenas se llama a persist, asi que ahi salta el error
    @Test
    void databaseRejectsTwoActiveSlotsAtTheSameTime() {
        slotAtStart();

        assertThrows(PersistenceException.class, this::slotAtStart);
    }

    @Test
    void reservationKeepsItsPriceAfterTheSlotPriceChanges() {
        Tourist tourist = entityManager.persist(Tourist.builder()
                .firstName("Ana").lastName("Pérez").username("ana").email("ana@mail.com")
                .passwordHash("x").birthDate(LocalDate.of(1990, 1, 1)).build());
        Slot slot = slotAtStart();
        Reservation reservation = entityManager.persist(new Reservation(tourist, slot,
                List.of(new Participant("Ana", "Pérez", LocalDate.of(1990, 1, 1)),
                        new Participant("Juan", "Pérez", LocalDate.of(1992, 3, 4))), NOW));
        slot.update(START, BigDecimal.valueOf(2500), 10, NOW);
        entityManager.flush();
        entityManager.clear();   // fuerza a leer de nuevo desde la base

        Reservation stored = entityManager.find(Reservation.class, reservation.getId());

        assertEquals(0, new BigDecimal("1500").compareTo(stored.getUnitPrice()));
        assertEquals(0, new BigDecimal("3000").compareTo(stored.getTotalAmount()));
        assertEquals(2, stored.getParticipants().size());
        assertEquals(8, entityManager.find(Slot.class, slot.getId()).getAvailability());
    }
}
