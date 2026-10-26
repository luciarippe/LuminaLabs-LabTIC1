package uy.edu.um.luminalabs.weather;

import org.junit.jupiter.api.Test;
import uy.edu.um.luminalabs.entities.Department;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Como se lee la respuesta de Open-Meteo (sin llamar a la API)
class OpenMeteoWeatherClientTest {

    @Test
    void picksThePlaceOfTheRightDepartment() {
        List<OpenMeteoWeatherClient.Place> places = List.of(
                new OpenMeteoWeatherClient.Place("Florida", -30.1, -55.1, "Departamento de Rivera"),
                new OpenMeteoWeatherClient.Place("Florida", -34.1, -56.2, "departamento de Florida"));

        assertEquals(Optional.of(new Coordinates(-34.1, -56.2)), OpenMeteoWeatherClient.pick(places, Department.FLORIDA));
        assertEquals(Optional.empty(), OpenMeteoWeatherClient.pick(places, Department.ROCHA));
    }

    @Test
    void departmentMatchIgnoresAccents() {
        List<OpenMeteoWeatherClient.Place> places = List.of(
                new OpenMeteoWeatherClient.Place("Paysandú", -32.3, -58.1, "Departamento de Paysandu"));

        assertTrue(OpenMeteoWeatherClient.pick(places, Department.PAYSANDU).isPresent());
    }

    @Test
    void readsHourlyListsAndSkipsHoursWithoutData() {
        OpenMeteoWeatherClient.Hourly hourly = new OpenMeteoWeatherClient.Hourly(
                List.of("2026-10-27T10:00", "2026-10-27T11:00", "2026-10-27T12:00"),
                Arrays.asList(21.6, null, 23.2),
                Arrays.asList(10, 20, null),
                Arrays.asList(2, 3, 61),
                Arrays.asList(12.4, 14.0, null));

        Map<LocalDateTime, WeatherForecast> forecasts = OpenMeteoWeatherClient.toForecasts(hourly);

        assertEquals(2, forecasts.size());
        WeatherForecast ten = forecasts.get(LocalDateTime.of(2026, 10, 27, 10, 0));
        assertEquals(22, ten.temperature());
        assertEquals(10, ten.rainProbability());
        assertEquals(WeatherCondition.PARTLY_CLOUDY, ten.condition());
        assertEquals(12, ten.windSpeed());
        WeatherForecast noon = forecasts.get(LocalDateTime.of(2026, 10, 27, 12, 0));
        assertNull(noon.rainProbability());
        assertTrue(noon.isRainLikely(), "codigo 61 es lluvia aunque no haya probabilidad");
    }

    @Test
    void wmoCodesMapToConditions() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0));
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmoCode(45));
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmoCode(81));
        assertEquals(WeatherCondition.STORM, WeatherCondition.fromWmoCode(95));
        assertFalse(WeatherCondition.CLOUDY.isWet());
    }

    @Test
    void highRainProbabilityIsAWarningEvenIfCloudy() {
        WeatherForecast forecast = new WeatherForecast(LocalDateTime.of(2026, 10, 27, 10, 0), 18, 60,
                WeatherCondition.CLOUDY, 20);

        assertTrue(forecast.isRainLikely());
        assertEquals("Nublado, 18 °C, 60 % de probabilidad de lluvia", forecast.getSummary());
    }
}
