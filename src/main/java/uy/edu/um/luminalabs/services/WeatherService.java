package uy.edu.um.luminalabs.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.weather.Coordinates;
import uy.edu.um.luminalabs.weather.OpenMeteoWeatherClient;
import uy.edu.um.luminalabs.weather.WeatherClient;
import uy.edu.um.luminalabs.weather.WeatherForecast;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// RF-15 y CU-07 paso 8: pronostico del clima para la fecha y el lugar de cada horario, si esta dentro del
// horizonte de pronostico. Si la API no responde, se devuelve vacio y las paginas se muestran sin pronostico (8a).
//
// Para no llamar a la API en cada pagina, se guardan en memoria las coordenadas de cada localidad (no cambian) y
// el pronostico de cada lugar durante FORECAST_TTL. Si la API fallo, se espera FAILURE_TTL antes de reintentar:
// asi, con la API caida, no se hace esperar el timeout en cada pagina.
@Slf4j
@Service
public class WeatherService {

    // El pronostico cubre hoy y los siguientes dias, hasta este limite
    public static final int HORIZON_DAYS = OpenMeteoWeatherClient.FORECAST_DAYS;

    static final Duration FORECAST_TTL = Duration.ofHours(1);
    static final Duration FAILURE_TTL = Duration.ofMinutes(2);

    // Si la API no encuentra la localidad, se usa la capital del departamento (el clima es parecido)
    private static final Map<Department, Coordinates> CAPITALS = new EnumMap<>(Map.ofEntries(
            Map.entry(Department.ARTIGAS, new Coordinates(-30.40, -56.47)),
            Map.entry(Department.CANELONES, new Coordinates(-34.52, -56.28)),
            Map.entry(Department.CERRO_LARGO, new Coordinates(-32.37, -54.17)),
            Map.entry(Department.COLONIA, new Coordinates(-34.47, -57.84)),
            Map.entry(Department.DURAZNO, new Coordinates(-33.38, -56.52)),
            Map.entry(Department.FLORES, new Coordinates(-33.52, -56.90)),
            Map.entry(Department.FLORIDA, new Coordinates(-34.10, -56.21)),
            Map.entry(Department.LAVALLEJA, new Coordinates(-34.38, -55.24)),
            Map.entry(Department.MALDONADO, new Coordinates(-34.90, -54.95)),
            Map.entry(Department.MONTEVIDEO, new Coordinates(-34.90, -56.19)),
            Map.entry(Department.PAYSANDU, new Coordinates(-32.32, -58.08)),
            Map.entry(Department.RIO_NEGRO, new Coordinates(-33.13, -58.30)),
            Map.entry(Department.RIVERA, new Coordinates(-30.90, -55.55)),
            Map.entry(Department.ROCHA, new Coordinates(-34.48, -54.33)),
            Map.entry(Department.SALTO, new Coordinates(-31.39, -57.96)),
            Map.entry(Department.SAN_JOSE, new Coordinates(-34.34, -56.71)),
            Map.entry(Department.SORIANO, new Coordinates(-33.25, -58.03)),
            Map.entry(Department.TACUAREMBO, new Coordinates(-31.71, -55.98)),
            Map.entry(Department.TREINTA_Y_TRES, new Coordinates(-33.23, -54.38))));

    private final WeatherClient client;
    private final Clock clock;
    private final boolean enabled;

    private final Map<Long, Coordinates> coordinatesByLocation = new ConcurrentHashMap<>();
    private final Map<Coordinates, CachedForecast> forecasts = new ConcurrentHashMap<>();

    public WeatherService(WeatherClient client, Clock clock, @Value("${app.weather.enabled:true}") boolean enabled) {
        this.client = client;
        this.clock = clock;
        this.enabled = enabled;
    }

    // Pronostico de cada horario (clave: su inicio) que este dentro del horizonte. Los demas no aparecen.
    public Map<LocalDateTime, WeatherForecast> forecastFor(Location location, Collection<LocalDateTime> starts) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime horizonEnd = now.toLocalDate().plusDays(HORIZON_DAYS).atStartOfDay();
        boolean anyWithinHorizon = starts.stream().anyMatch(start -> isWithin(start, now, horizonEnd));
        // Sin horarios en el horizonte no se llama a la API
        if (!enabled || !anyWithinHorizon) {
            return Map.of();
        }
        Map<LocalDateTime, WeatherForecast> hourly = hourlyForecast(coordinatesOf(location));
        Map<LocalDateTime, WeatherForecast> result = new HashMap<>();
        for (LocalDateTime start : starts) {
            WeatherForecast forecast = isWithin(start, now, horizonEnd) ? hourly.get(nearestHour(start)) : null;
            if (forecast != null) {
                result.put(start, forecast);
            }
        }
        return result;
    }

    public Optional<WeatherForecast> forecastFor(Location location, LocalDateTime start) {
        return Optional.ofNullable(forecastFor(location, java.util.List.of(start)).get(start));
    }

    // El administrador cambio el nombre o el departamento de la localidad: se vuelven a buscar sus coordenadas
    public void forgetLocation(Long locationId) {
        coordinatesByLocation.remove(locationId);
    }

    private static boolean isWithin(LocalDateTime start, LocalDateTime now, LocalDateTime horizonEnd) {
        return !start.isBefore(now.truncatedTo(ChronoUnit.HOURS)) && start.isBefore(horizonEnd);
    }

    // El pronostico es por hora: un horario de las 17:30 usa el de las 18:00
    static LocalDateTime nearestHour(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return time.getMinute() >= 30 ? hour.plusHours(1) : hour;
    }

    private Coordinates coordinatesOf(Location location) {
        Coordinates cached = coordinatesByLocation.get(location.getId());
        if (cached != null) {
            return cached;
        }
        Coordinates capital = CAPITALS.get(location.getDepartment());
        try {
            Coordinates coordinates = client.geocode(location.getName(), location.getDepartment()).orElse(capital);
            coordinatesByLocation.put(location.getId(), coordinates);
            return coordinates;
        } catch (RuntimeException e) {
            // No se guarda: la proxima vez se vuelve a intentar encontrar la localidad
            log.warn("Could not geocode {}: {}", location.getName(), e.toString());
            return capital;
        }
    }

    private Map<LocalDateTime, WeatherForecast> hourlyForecast(Coordinates coordinates) {
        LocalDateTime now = LocalDateTime.now(clock);
        CachedForecast cached = forecasts.get(coordinates);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.forecasts();
        }
        try {
            Map<LocalDateTime, WeatherForecast> fresh = client.hourlyForecast(coordinates);
            forecasts.put(coordinates, new CachedForecast(fresh, now.plus(FORECAST_TTL)));
            return fresh;
        } catch (RuntimeException e) {
            log.warn("Weather API unavailable: {}", e.toString());
            forecasts.put(coordinates, new CachedForecast(Map.of(), now.plus(FAILURE_TTL)));
            return Map.of();
        }
    }

    private record CachedForecast(Map<LocalDateTime, WeatherForecast> forecasts, LocalDateTime expiresAt) {
    }
}
