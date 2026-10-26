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
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.repositories.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// DATOS DE DEMOSTRACION, solo para desarrollo: turistas, reservas ya terminadas y valoraciones (CU-08) en algunas
// actividades de DemoDataInitializer, para ver el resumen y "lo que mas destacan" sin esperar a que terminen
// reservas reales. Va aparte de DemoDataInitializer porque ese solo corre con la base vacia.
// Se carga una sola vez (si no existe el turista de demo). Se desactiva igual: app.demo-data.enabled=false.
//
// Turista para probar: demo.turista / Demo12345. Tiene una reserva terminada sin valorar (para probar el
// formulario) y otra ya valorada.
@Slf4j
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoRatingsInitializer implements ApplicationRunner {

    private static final String DEMO_TOURIST = "demo.turista";
    private static final String PASSWORD = "Demo12345";

    private static final String KAYAK = "Kayak al atardecer en la Laguna de Rocha";
    private static final String CABO_POLONIO = "Caminata a Cabo Polonio y su colonia de lobos marinos";
    private static final String COLONIA = "Barrio Histórico de Colonia del Sacramento";
    private static final String CABALGATA = "Cabalgata por las sierras de Villa Serrana";

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RatingRepository ratingRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private Map<String, Activity> activities;
    private List<Tourist> reviewers;
    private int nextReviewer;

    // Una valoracion de ejemplo: hace cuantos dias fue la actividad, estrellas, titular y resena
    private record Sample(int daysAgo, int stars, String title, String comment) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(DEMO_TOURIST)) {
            return;
        }
        activities = activityRepository.findAll().stream()
                .collect(Collectors.toMap(Activity::getName, Function.identity(), (first, second) -> first));
        if (!activities.containsKey(KAYAK)) {
            // No estan las actividades de demo (la base se cargo a mano): no hay donde poner las valoraciones
            return;
        }
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        Tourist demoTourist = tourist(DEMO_TOURIST, "Sofía", "Martínez", encodedPassword);
        reviewers = new ArrayList<>();
        String[][] names = {{"Ana", "Pérez"}, {"Martín", "Rodríguez"}, {"Valentina", "González"}, {"Lucas", "Fernández"},
                {"Camila", "Silva"}, {"Diego", "López"}, {"Florencia", "Sosa"}, {"Joaquín", "Méndez"}};
        for (int i = 0; i < names.length; i++) {
            reviewers.add(tourist("demo.resena" + (i + 1), names[i][0], names[i][1], encodedPassword));
        }

        int created = 0;
        created += rate(KAYAK, List.of(
                new Sample(12, 5, "El mejor atardecer de mi vida",
                        "Los guías fueron súper atentos y nos explicaron todo sobre las aves de la laguna. Vimos flamencos "
                                + "y cisnes de cuello negro. El remo es tranquilo, apto para cualquiera aunque nunca hayas hecho kayak."),
                new Sample(19, 5, "Imperdible si estás en La Paloma",
                        "Experiencia hermosa y muy bien organizada. Los kayaks estaban en perfecto estado y el atardecer "
                                + "sobre la laguna es increíble."),
                new Sample(26, 4, "Muy linda, llevá abrigo",
                        "La actividad es muy buena, pero a la vuelta refresca bastante. Los guías conocen muchísimo de la zona."),
                new Sample(33, 5, "Perfecto para ir en pareja",
                        "Fuimos con mi pareja y nos encantó. Tranquilo, seguro y con paisajes increíbles. Volveríamos sin dudar."),
                new Sample(41, 5, "Excelente guía",
                        "Nos contaron la historia de la laguna y nos ayudaron con la técnica de remo. Muy recomendable."),
                new Sample(48, 4, "Hermoso, aunque un poco corto",
                        "Me hubiera gustado quedarme un rato más en el agua, pero el recorrido vale la pena."),
                new Sample(55, 3, "Lindo, pero con mucho viento",
                        "El día que fuimos había mucho viento y costó remar. No es culpa de la empresa, pero podrían avisar antes."),
                new Sample(63, 5, "Una experiencia única",
                        "Nunca había hecho kayak y fue facilísimo. Ver el sol bajar sobre el agua no tiene precio.")));
        created += rate(CABO_POLONIO, List.of(
                new Sample(9, 5, "Los lobos marinos, un espectáculo",
                        "Desde el mirador del faro se ven cientos de lobos marinos. El guía sabe muchísimo de la historia del "
                                + "pueblo y de los naufragios. La caminata por las dunas es exigente pero preciosa."),
                new Sample(16, 5, "Cabo Polonio es mágico",
                        "Un lugar sin luz eléctrica ni autos, y el recorrido te lo muestra con mucho respeto. Imperdible."),
                new Sample(30, 4, "Muy buena, llevá buen calzado",
                        "Se camina bastante por arena. Todo muy bien organizado y el traslado en camión es parte de la aventura."),
                new Sample(44, 4, "Linda caminata",
                        "Buena experiencia, aunque el grupo era grande y a veces costaba escuchar al guía."),
                new Sample(58, 2, "Esperaba más",
                        "El traslado salió con mucha demora y el tiempo en el faro fue corto. El paisaje igual es hermoso.")));
        created += rate(COLONIA, List.of(
                new Sample(14, 5, "Historia contada con pasión",
                        "La guía hizo que la historia entre portugueses y españoles fuera entretenidísima. Subir al faro es lo mejor."),
                new Sample(27, 4, "Muy recomendable",
                        "Recorrido tranquilo por las calles empedradas, con muchos datos curiosos. Ideal para una mañana."),
                new Sample(40, 4, "Lindo paseo", "Bien organizado y a buen precio. La Calle de los Suspiros es preciosa.")));
        created += rate(CABALGATA, List.of(
                new Sample(21, 5, "Paisajes increíbles",
                        "Los caballos son mansos y el guía muy cuidadoso. El mate al pie del Salto del Penitente, un lujo."),
                new Sample(37, 4, "Muy linda experiencia",
                        "Ideal para desconectar. Para los que nunca anduvieron a caballo, van despacio y te explican todo.")));

        // El turista de demo: una ya valorada (Colonia) y una terminada hace poco, sin valorar, para probar el formulario
        rating(finishedReservation(demoTourist, activities.get(COLONIA), 6, 2), 5, "Volvería mañana mismo",
                "Fuimos en familia y a los chicos les encantó subir al faro. La guía, un siete.");
        finishedReservation(demoTourist, activities.get(KAYAK), 3, 2);
        log.info("Demo ratings created: {} ratings ({} / {})", created + 1, DEMO_TOURIST, PASSWORD);
    }

    private Tourist tourist(String username, String firstName, String lastName, String encodedPassword) {
        return userRepository.save(Tourist.builder()
                .firstName(firstName).lastName(lastName)
                .username(username).email(username + "@uruguaymnatural.uy")
                .passwordHash(encodedPassword)
                .birthDate(LocalDate.of(1992, 3, 15))
                .build());
    }

    // Cada valoracion es de un turista distinto (rotando entre los de demo)
    private int rate(String activityName, List<Sample> samples) {
        Activity activity = activities.get(activityName);
        if (activity == null) {
            return 0;
        }
        for (Sample sample : samples) {
            Tourist tourist = reviewers.get(nextReviewer++ % reviewers.size());
            int participants = 1 + sample.daysAgo() % 3;
            rating(finishedReservation(tourist, activity, sample.daysAgo(), participants),
                    sample.stars(), sample.title(), sample.comment());
        }
        return samples.size();
    }

    // Un horario que ya paso y una reserva pagada y terminada en el (como la deja CU-18). Si la actividad ya tiene
    // un horario activo a esa hora (ej. uno de DemoDataInitializer que quedo en el pasado) se usa ese: no puede
    // haber dos (restriccion unica uk_slots_activity_active_start).
    private Reservation finishedReservation(Tourist tourist, Activity activity, int daysAgo, int participants) {
        LocalTime time = activity.getSlots().isEmpty() ? LocalTime.of(10, 0) : activity.getSlots().getFirst().getStartTime().toLocalTime();
        BigDecimal price = activity.getSlots().isEmpty() ? BigDecimal.valueOf(1500) : activity.getSlots().getFirst().getPrice();
        LocalDateTime start = LocalDate.now(clock).minusDays(daysAgo).atTime(time);
        Slot slot = activity.getSlots().stream()
                .filter(existing -> existing.isActive() && existing.getStartTime().equals(start)
                        && existing.getAvailability() >= participants)
                .findFirst()
                .orElseGet(() -> slotRepository.save(activity.addSlot(new Slot(activity, start, price, 12))));
        List<Participant> people = IntStream.range(0, participants)
                .mapToObj(i -> new Participant(tourist.getFirstName(), tourist.getLastName(), tourist.getBirthDate()))
                .toList();
        Reservation reservation = new Reservation(tourist, slot, people, start.minusDays(4));
        reservation.confirm();
        reservation.finish();
        return reservationRepository.save(reservation);
    }

    private void rating(Reservation reservation, int stars, String title, String comment) {
        LocalDateTime ratedAt = reservation.getEndTime().plusDays(1);
        ratingRepository.save(new Rating(reservation, stars, title, comment, ratedAt));
    }
}
