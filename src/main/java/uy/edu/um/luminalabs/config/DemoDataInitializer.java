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
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// DATOS DE DEMOSTRACION, solo para desarrollo: emprendimientos y actividades ficticias para ver el catalogo
// mientras no exista la publicacion de actividades (CU-03). Se cargan una sola vez, si no hay actividades.
// Desactivar con app.demo-data.enabled=false en application.properties.
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_USERNAME = "demo.prestador";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ActivityRepository activityRepository;
    private final LocationRepository locationRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private Map<String, Location> locations;
    private Map<String, ActivityCategory> categories;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (activityRepository.count() > 0 || userRepository.existsByUsernameIgnoreCase(DEMO_USERNAME)) {
            return;
        }
        locations = locationRepository.findAll().stream().collect(Collectors.toMap(Location::getName, Function.identity()));
        categories = categoryRepository.findAll().stream().collect(Collectors.toMap(ActivityCategory::getName, Function.identity()));

        Provider provider = userRepository.save(Provider.builder()
                .firstName("Demo").lastName("Prestador")
                .username(DEMO_USERNAME).email("demo.prestador@uruguaymnatural.uy")
                .passwordHash(passwordEncoder.encode("Demo12345"))
                .birthDate(LocalDate.of(1990, 1, 1))
                .build());

        Business costa = business(provider, BusinessType.COMPANY, "Costa Este Experiencias", "219999990011",
                "Excursiones guiadas en la costa de Rocha y Maldonado.", "La Paloma", "Punta del Diablo", "Punta del Este");
        Business sierras = business(provider, BusinessType.INDIVIDUAL, "Sierras de Lavalleja", "19999991",
                "Cabalgatas y caminatas por las sierras del este.", "Villa Serrana", "Minas");
        Business sabores = business(provider, BusinessType.COMPANY, "Sabores del Río de la Plata", "219999990022",
                "Recorridos gastronómicos y visitas a bodegas.", "Carmelo", "Colonia del Sacramento", "Montevideo", "José Ignacio");
        Business litoral = business(provider, BusinessType.COMPANY, "Litoral Termal", "219999990033",
                "Bienestar y naturaleza en el litoral oeste.", "Termas del Daymán", "Salto");

        int index = 0;
        activity(index++, costa, "Kayak al atardecer en la Laguna de Rocha", "La Paloma",
                "Remá por la laguna costera más grande del país mientras cae el sol, acompañado por guías locales. "
                        + "Vas a recorrer bañados y avistar cisnes de cuello negro, garzas y flamencos.",
                "Parador La Balconada, La Paloma", 150, 12, 1900, LocalTime.of(17, 30),
                "Kayak doble, remo, chaleco salvavidas y guía bilingüe.", "Ropa cómoda que se pueda mojar, protector solar y agua.",
                "Actividades acuáticas", "Turismo de naturaleza");
        activity(index++, costa, "Caminata a Cabo Polonio y su colonia de lobos marinos", "Cabo Polonio",
                "Una caminata por las dunas hasta el faro de Cabo Polonio, con historia del pueblo y observación "
                        + "de la colonia de lobos y leones marinos desde el mirador.",
                "Terminal de acceso a Cabo Polonio, Ruta 10 km 264", 240, null, 1500, LocalTime.of(9, 30),
                "Guía, entrada al faro y traslado en camión 4x4.", "Calzado cerrado para arena y abrigo liviano.",
                "Senderismo", "Turismo de naturaleza");
        activity(index++, costa, "Clase de surf para principiantes en Punta del Diablo", "Punta del Diablo",
                "Aprendé lo básico del surf en la Playa de la Viuda con instructores certificados: "
                        + "seguridad en el agua, remada y tus primeras olas.",
                "Playa de la Viuda, bajada principal", 120, 8, 1600, LocalTime.of(10, 0),
                "Tabla, traje de neopreno e instructor.", "Saber nadar. Traer toalla y protector solar.",
                "Aventura", "Actividades acuáticas");
        activity(index++, costa, "Avistaje de ballenas francas en Punta del Este", "Punta del Este",
                "Salida embarcada para observar ballenas francas australes durante su paso por la costa uruguaya, "
                        + "con explicación de biólogos marinos.",
                "Puerto de Punta del Este, muelle 4", 120, 6, 3200, LocalTime.of(11, 0),
                "Navegación, chaleco salvavidas y binoculares.", "Abrigo y gorro: en el agua hace más frío.",
                "Turismo de naturaleza", "Actividades acuáticas");
        activity(index++, sierras, "Cabalgata por las sierras de Villa Serrana", "Villa Serrana",
                "Recorré a caballo los cerros y quebradas de Villa Serrana, con paradas en miradores "
                        + "y un mate al pie del Salto del Penitente.",
                "Posta del Viajero, Villa Serrana", 180, 10, 2400, LocalTime.of(9, 0),
                "Caballo, casco y guía baqueano.", "Pantalón largo y calzado cerrado.",
                "Cabalgatas", "Turismo de naturaleza");
        activity(index++, sierras, "Trekking al Cerro Arequita y sus cavernas", "Minas",
                "Ascenso al Cerro Arequita con visita a la caverna de los murciélagos y vista panorámica "
                        + "del valle del río Santa Lucía.",
                "Parque Salus, entrada principal", 300, 14, 1700, LocalTime.of(8, 30),
                "Guía de montaña, linterna frontal y seguro.", "Calzado de trekking, 2 litros de agua y almuerzo liviano.",
                "Trekking", "Aventura");
        activity(index++, sabores, "Degustación de Tannat en una bodega familiar", "Carmelo",
                "Visita guiada por viñedos y bodega, con degustación de cinco vinos de autor maridados "
                        + "con quesos artesanales de la zona.",
                "Camino de los Peregrinos s/n, Carmelo", 120, 18, 2800, LocalTime.of(16, 0),
                "Degustación de 5 vinos y tabla de quesos.", null,
                "Enoturismo", "Gastronomía");
        activity(index++, sabores, "Barrio Histórico de Colonia del Sacramento", "Colonia del Sacramento",
                "Un recorrido a pie por las calles empedradas del Barrio Histórico, Patrimonio de la Humanidad, "
                        + "con historia de la disputa entre portugueses y españoles.",
                "Portón de Campo, Colonia del Sacramento", 120, null, 900, LocalTime.of(10, 30),
                "Guía y entrada al Faro.", "Calzado cómodo.",
                "Recorridos históricos");
        activity(index++, sabores, "Ciudad Vieja y Mercado del Puerto a pie", "Montevideo",
                "Descubrí la historia de Montevideo desde la Puerta de la Ciudadela hasta el Mercado del Puerto, "
                        + "con degustación de productos típicos en el camino.",
                "Puerta de la Ciudadela, Plaza Independencia", 180, null, 1200, LocalTime.of(10, 0),
                "Guía y degustación de tres productos típicos.", "Calzado cómodo.",
                "Recorridos históricos", "Gastronomía");
        activity(index++, sabores, "Cena de autor al atardecer en José Ignacio", "José Ignacio",
                "Menú de pasos con productos de huerta y pesca del día, servido frente al mar en el horario del atardecer.",
                "Calle Los Teros esquina Las Garzas, José Ignacio", 150, 18, 4500, LocalTime.of(19, 0),
                "Menú de 5 pasos con maridaje.", "Avisar restricciones alimentarias al reservar.",
                "Gastronomía");
        activity(index++, litoral, "Día de termas y bienestar en el Daymán", "Termas del Daymán",
                "Jornada en el complejo termal con acceso a piscinas de agua termal, circuito de hidromasajes "
                        + "y una sesión guiada de relajación.",
                "Complejo Termal Daymán, Ruta 3 km 487", 360, null, 1300, LocalTime.of(10, 0),
                "Entrada al complejo y sesión guiada.", "Traje de baño, toalla y ojotas.",
                "Actividades acuáticas", "Turismo de naturaleza");
        log.info("Demo data created: {} activities", index);
    }

    private Business business(Provider provider, BusinessType type, String name, String identifier,
                              String description, String... locationNames) {
        Business business = new Business();
        business.setType(type);
        business.setLegalName(name);
        business.setIdentifier(identifier);
        business.setDescription(description);
        business.setContactEmail("contacto@" + identifier + ".demo");
        business.setContactPhone("099 000 000");
        business.setBankName("Banco de demostración");
        business.setBankAccountNumber("000000000");
        Arrays.stream(locationNames).map(locations::get).forEach(business.getLocations()::add);
        business.addProvider(provider);
        business.approve();
        return businessRepository.save(business);
    }

    private void activity(int index, Business business, String name, String locationName, String description,
                          String address, int durationMinutes, Integer minimumAge, int price, LocalTime time,
                          String inclusions, String importantDetails, String... categoryNames) {
        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setLocation(locations.get(locationName));
        activity.setName(name);
        activity.setDescription(description);
        activity.setAddress(address);
        activity.setDurationMinutes(durationMinutes);
        activity.setLanguage("Español, inglés");
        activity.setMinimumAge(minimumAge);
        activity.setAccessibility("Consultar por necesidades de accesibilidad antes de reservar.");
        activity.setInclusions(inclusions);
        activity.setEquipment("Ropa cómoda y protector solar.");
        activity.setImportantDetails(importantDetails);
        activity.setBookingCutoffMinutes(120);
        activity.setCancellationDeadlineHours(24);
        activity.setCancellationFeePercentage(20);
        Arrays.stream(categoryNames).map(categories::get).forEach(activity.getCategories()::add);

        // Fechas cada 3 dias durante las proximas 6 semanas, empezando en un dia distinto para cada actividad
        LocalDate firstDay = LocalDate.now(clock).plusDays(1 + index % 3);
        for (LocalDate day = firstDay; day.isBefore(firstDay.plusWeeks(6)); day = day.plusDays(3)) {
            activity.addSlot(new Slot(activity, day.atTime(time), BigDecimal.valueOf(price), 12));
        }
        activityRepository.save(activity);
    }
}
