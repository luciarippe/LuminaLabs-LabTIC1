package uy.edu.um.luminalabs.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;

// Carga los datos minimos para usar la plataforma: el primer administrador, las localidades y las
// categorias. Las localidades y categorias despues las mantiene el administrador (CU-10).
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;
    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.admin.password}")
    private String adminPassword;
    @Value("${app.admin.first-name}")
    private String adminFirstName;
    @Value("${app.admin.last-name}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createInitialAdmin();
        createInitialLocations();
        createInitialCategories();
    }

    // Categorias mencionadas en la letra del proyecto
    private void createInitialCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }
        categoryRepository.saveAll(List.of(
                new ActivityCategory("Turismo de naturaleza", "Experiencias en entornos naturales."),
                new ActivityCategory("Trekking", "Caminatas de mediana y larga distancia."),
                new ActivityCategory("Aventura", "Actividades con un componente de desafío físico."),
                new ActivityCategory("Gastronomía", "Cocina local, productores y degustaciones."),
                new ActivityCategory("Actividades acuáticas", "En el mar, ríos, lagunas y termas."),
                new ActivityCategory("Recorridos históricos", "Patrimonio, arquitectura e historia."),
                new ActivityCategory("Enoturismo", "Bodegas, viñedos y degustación de vinos."),
                new ActivityCategory("Cabalgatas", "Paseos a caballo."),
                new ActivityCategory("Senderismo", "Caminatas por senderos señalizados.")));
        log.info("Initial activity categories created");
    }

    private void createInitialAdmin() {
        if (userRepository.countAdmins() > 0) {
            return;
        }
        Admin admin = Admin.builder()
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .username(adminUsername)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
        userRepository.save(admin);
        log.info("Initial admin user '{}' created", adminUsername);
    }

    private void createInitialLocations() {
        if (locationRepository.count() > 0) {
            return;
        }
        locationRepository.saveAll(List.of(
                new Location("Artigas", Department.ARTIGAS),
                new Location("Canelones", Department.CANELONES),
                new Location("Atlántida", Department.CANELONES),
                new Location("Melo", Department.CERRO_LARGO),
                new Location("Colonia del Sacramento", Department.COLONIA),
                new Location("Carmelo", Department.COLONIA),
                new Location("Durazno", Department.DURAZNO),
                new Location("Trinidad", Department.FLORES),
                new Location("Florida", Department.FLORIDA),
                new Location("Minas", Department.LAVALLEJA),
                new Location("Villa Serrana", Department.LAVALLEJA),
                new Location("Maldonado", Department.MALDONADO),
                new Location("Punta del Este", Department.MALDONADO),
                new Location("Piriápolis", Department.MALDONADO),
                new Location("José Ignacio", Department.MALDONADO),
                new Location("Montevideo", Department.MONTEVIDEO),
                new Location("Paysandú", Department.PAYSANDU),
                new Location("Fray Bentos", Department.RIO_NEGRO),
                new Location("Rivera", Department.RIVERA),
                new Location("Rocha", Department.ROCHA),
                new Location("La Paloma", Department.ROCHA),
                new Location("Cabo Polonio", Department.ROCHA),
                new Location("Punta del Diablo", Department.ROCHA),
                new Location("Salto", Department.SALTO),
                new Location("Termas del Daymán", Department.SALTO),
                new Location("San José de Mayo", Department.SAN_JOSE),
                new Location("Mercedes", Department.SORIANO),
                new Location("Tacuarembó", Department.TACUAREMBO),
                new Location("San Gregorio de Polanco", Department.TACUAREMBO),
                new Location("Treinta y Tres", Department.TREINTA_Y_TRES)));
        log.info("Initial locations created");
    }
}
