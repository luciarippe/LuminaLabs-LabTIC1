package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.weather.Coordinates;
import uy.edu.um.luminalabs.weather.WeatherClient;
import uy.edu.um.luminalabs.weather.WeatherCondition;
import uy.edu.um.luminalabs.weather.WeatherForecast;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// RF-15 y CU-07 paso 8: pronostico de los horarios
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Coordinates LA_PALOMA = new Coordinates(-34.66, -54.16);

    @Mock
    private WeatherClient client;

    private MutableClock clock;
    private WeatherService service;
    private Location location;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW.atZone(ZONE).toInstant());
        service = new WeatherService(client, clock, true);
        location = new Location("La Paloma", Department.ROCHA);
        location.setId(1L);
    }

    private static WeatherForecast forecast(LocalDateTime time, int temperature) {
        return new WeatherForecast(time, temperature, 10, WeatherCondition.CLEAR, 12);
    }

    @Test
    void returnsForecastOfEachSlotWithinHorizonUsingNearestHour() {
        LocalDateTime tomorrowAtHalfPast = NOW.plusDays(1).withHour(17).withMinute(30);
        LocalDateTime farAway = NOW.plusDays(30);
        when(client.geocode("La Paloma", Department.ROCHA)).thenReturn(Optional.of(LA_PALOMA));
        when(client.hourlyForecast(LA_PALOMA)).thenReturn(Map.of(
                tomorrowAtHalfPast.withMinute(0).plusHours(1), forecast(tomorrowAtHalfPast, 21)));

        Map<LocalDateTime, WeatherForecast> result = service.forecastFor(location, List.of(tomorrowAtHalfPast, farAway));

        assertEquals(21, result.get(tomorrowAtHalfPast).temperature(), "17:30 usa el pronostico de las 18:00");
        assertFalse(result.containsKey(farAway), "fuera del horizonte no hay pronostico");
    }

    @Test
    void doesNotCallTheApiWhenNoSlotIsWithinHorizon() {
        assertTrue(service.forecastFor(location, List.of(NOW.plusDays(40))).isEmpty());
        verifyNoInteractions(client);
    }

    // 8a: si la API no responde, no hay pronostico (y no se rompe nada)
    @Test
    void apiFailureMeansNoForecast() {
        when(client.geocode(any(), any())).thenReturn(Optional.of(LA_PALOMA));
        when(client.hourlyForecast(LA_PALOMA)).thenThrow(new IllegalStateException("timeout"));

        assertTrue(service.forecastFor(location, List.of(NOW.plusDays(1))).isEmpty());
    }

    // Con la API caida no se reintenta en cada pagina, pero si pasado un rato
    @Test
    void failureIsRetriedOnlyAfterAWhile() {
        when(client.geocode(any(), any())).thenReturn(Optional.of(LA_PALOMA));
        when(client.hourlyForecast(LA_PALOMA)).thenThrow(new IllegalStateException("timeout"));

        service.forecastFor(location, List.of(NOW.plusDays(1)));
        service.forecastFor(location, List.of(NOW.plusDays(1)));
        verify(client, times(1)).hourlyForecast(LA_PALOMA);

        clock.advance(WeatherService.FAILURE_TTL.plusSeconds(1));
        service.forecastFor(location, List.of(NOW.plusDays(1)));
        verify(client, times(2)).hourlyForecast(LA_PALOMA);
    }

    // El pronostico y las coordenadas se guardan: dos paginas seguidas hacen una sola llamada
    @Test
    void forecastAndCoordinatesAreCached() {
        LocalDateTime slot = NOW.plusDays(2).withHour(10);
        when(client.geocode(any(), any())).thenReturn(Optional.of(LA_PALOMA));
        when(client.hourlyForecast(LA_PALOMA)).thenReturn(Map.of(slot, forecast(slot, 18)));

        service.forecastFor(location, List.of(slot));
        Optional<WeatherForecast> second = service.forecastFor(location, slot);

        assertEquals(18, second.orElseThrow().temperature());
        verify(client, times(1)).geocode(any(), any());
        verify(client, times(1)).hourlyForecast(any());
    }

    // Si la API no conoce la localidad, se usa la capital del departamento
    @Test
    void unknownLocationUsesDepartmentCapital() {
        LocalDateTime slot = NOW.plusDays(1).withHour(10);
        Coordinates rochaCapital = new Coordinates(-34.48, -54.33);
        when(client.geocode(any(), any())).thenReturn(Optional.empty());
        when(client.hourlyForecast(rochaCapital)).thenReturn(Map.of(slot, forecast(slot, 19)));

        assertEquals(19, service.forecastFor(location, slot).orElseThrow().temperature());
    }

    @Test
    void disabledNeverCallsTheApi() {
        WeatherService disabled = new WeatherService(client, clock, false);

        assertTrue(disabled.forecastFor(location, List.of(NOW.plusDays(1))).isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void nearestHourRoundsHalfHoursUp() {
        assertEquals(NOW.withHour(10), WeatherService.nearestHour(NOW.withHour(10).withMinute(29)));
        assertEquals(NOW.withHour(11), WeatherService.nearestHour(NOW.withHour(10).withMinute(30)));
    }

    // Reloj que se puede adelantar, para probar cuando vence lo guardado
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(java.time.Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
