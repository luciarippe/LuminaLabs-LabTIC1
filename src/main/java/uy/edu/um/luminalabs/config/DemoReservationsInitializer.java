package uy.edu.um.luminalabs.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uy.edu.um.luminalabs.config.DemoCatalog.ActivityData;
import uy.edu.um.luminalabs.config.DemoCatalog.ReviewData;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.repositories.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// DATOS DE DEMOSTRACION, solo para desarrollo: los turistas de demo/data.json, con reservas terminadas y valoradas
// (CU-08) y reservas confirmadas en los proximos horarios, para que el catalogo muestre opiniones y el espacio del
// prestador tenga reservas que gestionar. Va aparte de DemoDataInitializer porque necesita las actividades ya
// creadas. Se carga una sola vez (si no existe el turista de demo). Se desactiva igual: app.demo-data.enabled=false.
//
// Turista para probar: demo.turista / Demo12345. Tiene dos reservas proximas, una terminada sin valorar (para probar
// el formulario) y otra ya valorada.
@Slf4j
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoReservationsInitializer implements ApplicationRunner {

    private static final String DEMO_TOURIST = "demo.turista";
    // Reservas proximas: en los horarios de las proximas semanas, con esta probabilidad por horario (mas alta en las
    // del prestador de demo, para que su espacio tenga movimiento). Random con semilla fija: siempre los mismos datos.
    private static final int UPCOMING_DAYS = 21;
    private static final double BOOKING_CHANCE = 0.3;
    private static final double DEMO_PROVIDER_BOOKING_CHANCE = 0.6;
    private static final String[] COMPANIONS = {"Lucía", "Mateo", "Martina", "Juan", "Sofía", "Bruno", "Emma", "Tomás"};

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final Random random = new Random(20261102);
    private List<Tourist> reviewers;
    private int nextReviewer;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(DEMO_TOURIST)) {
            return;
        }
        DemoCatalog catalog = DemoCatalog.read(objectMapper);
        Map<String, Activity> activities = activityRepository.findAll().stream()
                .collect(Collectors.toMap(Activity::getName, Function.identity(), (first, second) -> first));
        if (catalog.activities().stream().noneMatch(data -> activities.containsKey(data.name()))) {
            // No estan las actividades de demo (la base se cargo a mano): no hay donde poner las reservas
            return;
        }

        String encodedPassword = passwordEncoder.encode(DemoDataInitializer.PASSWORD);
        Tourist demoTourist = null;
        reviewers = new ArrayList<>();
        for (DemoCatalog.TouristData data : catalog.tourists()) {
            Tourist tourist = userRepository.save(Tourist.builder()
                    .firstName(data.firstName()).lastName(data.lastName())
                    .username(data.username()).email(data.username() + DemoDataInitializer.EMAIL_DOMAIN)
                    .passwordHash(encodedPassword)
                    .birthDate(data.birthDate())
                    .build());
            if (data.username().equals(DEMO_TOURIST)) {
                demoTourist = tourist;
            } else {
                reviewers.add(tourist);
            }
        }

        int ratings = 0;
        for (ActivityData data : catalog.activities()) {
            Activity activity = activities.get(data.name());
            if (activity == null) {
                continue;
            }
            for (ReviewData review : data.reviews()) {
                Tourist tourist = reviewers.get(nextReviewer++ % reviewers.size());
                rating(finishedReservation(tourist, activity, review.daysAgo(), 1 + review.daysAgo() % 3),
                        review.stars(), review.title(), review.comment());
                ratings++;
            }
        }

        int upcoming = 0;
        for (Activity activity : activities.values()) {
            upcoming += bookUpcomingSlots(activity);
        }

        if (demoTourist != null) {
            demoTouristTrips(demoTourist, activities);
        }
        log.info("Demo reservations created: {} ratings, {} upcoming reservations ({} / {})",
                ratings, upcoming, DEMO_TOURIST, DemoDataInitializer.PASSWORD);
    }

    // El turista de demo: una ya valorada, una terminada hace poco sin valorar (para probar el formulario) y dos proximas
    private void demoTouristTrips(Tourist tourist, Map<String, Activity> activities) {
        Activity colonia = activities.get("Barrio Histórico de Colonia del Sacramento");
        Activity caboPolonio = activities.get("Caminata a Cabo Polonio y su colonia de lobos marinos");
        Activity kayak = activities.get("Kayak al atardecer en la Laguna de Rocha");
        Activity casapueblo = activities.get("Atardecer en Casapueblo con copa de bienvenida");
        if (colonia != null) {
            rating(finishedReservation(tourist, colonia, 45, 2), 5, "Volvería mañana mismo",
                    "Fuimos en familia y a los chicos les encantó subir al faro. La guía, un siete.");
        }
        if (caboPolonio != null) {
            finishedReservation(tourist, caboPolonio, 3, 2);
        }
        if (kayak != null) {
            upcomingReservation(tourist, kayak, 4, 2);
        }
        if (casapueblo != null) {
            upcomingReservation(tourist, casapueblo, 12, 2);
        }
    }

    private int bookUpcomingSlots(Activity activity) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean demoProvider = activity.getBusiness().getProviders().stream()
                .anyMatch(provider -> provider.getUsername().equals(DemoDataInitializer.DEMO_PROVIDER));
        double chance = demoProvider ? DEMO_PROVIDER_BOOKING_CHANCE : BOOKING_CHANCE;
        int created = 0;
        for (Slot slot : activity.getSlots()) {
            if (!slot.isActive() || !slot.getStartTime().isAfter(now)
                    || slot.getStartTime().isAfter(now.plusDays(UPCOMING_DAYS)) || random.nextDouble() >= chance) {
                continue;
            }
            int bookings = 1 + random.nextInt(demoProvider ? 3 : 2);
            for (int i = 0; i < bookings && slot.getAvailability() > 0; i++) {
                int people = Math.min(1 + random.nextInt(4), slot.getAvailability());
                Tourist tourist = reviewers.get(random.nextInt(reviewers.size()));
                confirmedReservation(tourist, slot, people, now.minusDays(1 + random.nextInt(10)));
                created++;
            }
        }
        return created;
    }

    // Una reserva confirmada en el primer horario de la actividad a partir de dentro de "inDays" dias
    private void upcomingReservation(Tourist tourist, Activity activity, int inDays, int participants) {
        LocalDateTime from = LocalDate.now(clock).plusDays(inDays).atStartOfDay();
        activity.getSlots().stream()
                .filter(slot -> slot.isActive() && !slot.getStartTime().isBefore(from) && slot.getAvailability() >= participants)
                .findFirst()
                .ifPresent(slot -> confirmedReservation(tourist, slot, participants, LocalDateTime.now(clock).minusDays(2)));
    }

    // Un horario que ya paso y una reserva pagada y terminada en el (como la deja CU-18). Si la actividad ya tiene
    // un horario activo a esa hora se usa ese: no puede haber dos (restriccion unica uk_slots_activity_active_start).
    private Reservation finishedReservation(Tourist tourist, Activity activity, int daysAgo, int participants) {
        Slot reference = activity.getSlots().getFirst();
        LocalTime time = reference.getStartTime().toLocalTime();
        LocalDateTime start = LocalDate.now(clock).minusDays(daysAgo).atTime(time);
        Slot slot = activity.getSlots().stream()
                .filter(existing -> existing.isActive() && existing.getStartTime().equals(start)
                        && existing.getAvailability() >= participants)
                .findFirst()
                .orElseGet(() -> slotRepository.save(activity.addSlot(
                        new Slot(activity, start, reference.getPrice(), reference.getMaxCapacity()))));
        Reservation reservation = confirmedReservation(tourist, slot, participants, start.minusDays(4 + daysAgo % 9));
        reservation.finish();
        return reservation;
    }

    // Reserva con su cobro aprobado por la pasarela, como queda despues de pagar (CU-01)
    private Reservation confirmedReservation(Tourist tourist, Slot slot, int participants, LocalDateTime createdAt) {
        List<Participant> people = new ArrayList<>();
        people.add(new Participant(tourist.getFirstName(), tourist.getLastName(), tourist.getBirthDate()));
        for (int i = 1; i < participants; i++) {
            people.add(new Participant(COMPANIONS[random.nextInt(COMPANIONS.length)], tourist.getLastName(),
                    LocalDate.of(1975 + random.nextInt(30), 1 + random.nextInt(12), 1 + random.nextInt(28))));
        }
        Reservation reservation = new Reservation(tourist, slot, people, createdAt);
        reservation.confirm();
        reservationRepository.save(reservation);
        Payment charge = new Payment(reservation, PaymentType.CHARGE, reservation.getTotalAmount(), tourist.getFullName(),
                String.valueOf(1000 + random.nextInt(9000)), createdAt);
        charge.approve("DEMO-" + UUID.randomUUID(), createdAt.plusSeconds(3));
        paymentRepository.save(charge);
        return reservation;
    }

    private void rating(Reservation reservation, int stars, String title, String comment) {
        ratingRepository.save(new Rating(reservation, stars, title, comment, reservation.getEndTime().plusDays(1)));
    }
}
