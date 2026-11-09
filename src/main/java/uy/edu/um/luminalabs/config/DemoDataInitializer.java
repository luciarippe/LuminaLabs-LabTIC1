package uy.edu.um.luminalabs.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uy.edu.um.luminalabs.config.DemoCatalog.ActivityData;
import uy.edu.um.luminalabs.config.DemoCatalog.BusinessData;
import uy.edu.um.luminalabs.entities.*;
import uy.edu.um.luminalabs.repositories.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// DATOS DE DEMOSTRACION, solo para desarrollo: el catalogo de demo/data.json (prestadores, emprendimientos con sus
// redes, actividades con fotos y horarios, y fotos de los departamentos), para ver la plataforma como funcionaria
// de verdad. Se carga una sola vez, con la base sin actividades. Las reservas y valoraciones las carga
// DemoReservationsInitializer. Desactivar con app.demo-data.enabled=false en application.properties.
//
// Prestador para probar: demo.prestador / Demo12345 (Costa Este Experiencias y Sierras de Lavalleja).
// Todos los usuarios de demo tienen la misma contrasena.
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    public static final String DEMO_PROVIDER = "demo.prestador";
    public static final String PASSWORD = "Demo12345";
    public static final String EMAIL_DOMAIN = "@uruguaymnatural.uy";
    private static final int WEEKS_OF_SLOTS = 8;

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ActivityRepository activityRepository;
    private final LocationRepository locationRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final DepartmentPhotoRepository departmentPhotoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Los tests no cargan las fotos: en H2 no se crea la tabla de imagenes (ver application-test.properties)
    @Value("${app.demo-data.photos:true}")
    private boolean loadPhotos;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (activityRepository.count() > 0 || userRepository.existsByUsernameIgnoreCase(DEMO_PROVIDER)) {
            return;
        }
        DemoCatalog catalog = DemoCatalog.read(objectMapper);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Location> locations = locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getName, Function.identity()));
        Map<String, ActivityCategory> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ActivityCategory::getName, Function.identity()));

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        Map<String, Provider> providers = new HashMap<>();
        catalog.providers().forEach(data -> providers.put(data.username(), userRepository.save(Provider.builder()
                .firstName(data.firstName()).lastName(data.lastName())
                .username(data.username()).email(data.username() + EMAIL_DOMAIN)
                .passwordHash(encodedPassword)
                .birthDate(data.birthDate())
                .build())));

        Map<String, Business> businesses = new HashMap<>();
        for (BusinessData data : catalog.businesses()) {
            businesses.put(data.key(), business(data, providers, locations, now));
        }

        int index = 0;
        for (ActivityData data : catalog.activities()) {
            activity(data, index++, businesses.get(data.business()), locations, categories, now);
        }

        if (loadPhotos) {
            catalog.departmentPhotos().forEach((department, path) ->
                    departmentPhotoRepository.save(new DepartmentPhoto(department, DemoCatalog.photo(path, now))));
        }
        log.info("Demo data created: {} businesses, {} activities ({} / {})", businesses.size(), index, DEMO_PROVIDER, PASSWORD);
    }

    private Business business(BusinessData data, Map<String, Provider> providers, Map<String, Location> locations,
                              LocalDateTime now) {
        Business business = new Business();
        business.setType(data.type());
        business.setLegalName(data.name());
        business.setIdentifier(data.identifier());
        business.setDescription(data.description());
        business.setContactEmail(data.email());
        business.setContactPhone(data.phone());
        business.setBank(data.bank());
        business.setBankAccountNumber(data.account());
        business.setWebsiteUrl(SocialNetwork.WEBSITE.toUrl(data.website()));
        business.setInstagramUrl(SocialNetwork.INSTAGRAM.toUrl(data.instagram()));
        business.setFacebookUrl(SocialNetwork.FACEBOOK.toUrl(data.facebook()));
        business.setTiktokUrl(SocialNetwork.TIKTOK.toUrl(data.tiktok()));
        data.locations().stream().map(name -> require(locations, name)).forEach(business.getLocations()::add);
        data.providers().stream().map(providers::get).forEach(business::addProvider);
        if (data.status() == null || data.status() == BusinessStatus.APPROVED) {
            business.approve(now.minusMonths(4));
        } else if (data.status() == BusinessStatus.DENIED) {
            business.deny(data.statusReason(), now.minusDays(3));
        }
        return businessRepository.save(business);
    }

    private void activity(ActivityData data, int index, Business business, Map<String, Location> locations,
                          Map<String, ActivityCategory> categories, LocalDateTime now) {
        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setLocation(require(locations, data.location()));
        activity.setName(data.name());
        activity.setDescription(data.description());
        activity.setAddress(data.address());
        activity.setDurationMinutes(data.duration());
        activity.setLanguage(data.language());
        activity.setMinimumAge(data.minimumAge());
        activity.setAccessibility("Consultá por necesidades de accesibilidad antes de reservar.");
        activity.setInclusions(data.inclusions());
        activity.setEquipment(data.equipment());
        activity.setImportantDetails(data.importantDetails());
        // Condiciones algo distintas entre actividades, como las pondria cada prestador
        activity.setBookingCutoffMinutes(index % 3 == 0 ? 60 : 120);
        activity.setCancellationDeadlineMinutes((index % 2 == 0 ? 24 : 48) * 60);
        activity.setCancellationFeePercentage(index % 4 == 0 ? 0 : 20);
        data.categories().stream().map(name -> require(categories, name)).forEach(activity.getCategories()::add);
        if (loadPhotos) {
            data.photos().forEach(path -> activity.addPhoto(DemoCatalog.photo(path, now)));
        }

        LocalDate firstDay = LocalDate.now(clock).plusDays(1);
        LocalDate lastDay = firstDay.plusWeeks(data.weeks() == null ? WEEKS_OF_SLOTS : data.weeks());
        BigDecimal price = BigDecimal.valueOf(data.price());
        for (LocalDate day = firstDay; day.isBefore(lastDay); day = day.plusDays(1)) {
            if (data.weekdays().contains(day.getDayOfWeek().getValue())) {
                for (LocalTime time : data.times()) {
                    activity.addSlot(new Slot(activity, day.atTime(time), price, data.capacity()));
                }
            }
        }
        activityRepository.save(activity);
    }

    private static <T> T require(Map<String, T> values, String name) {
        T value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("Demo data references unknown '" + name + "'");
        }
        return value;
    }
}
