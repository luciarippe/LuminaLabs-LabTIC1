package uy.edu.um.luminalabs.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uy.edu.um.luminalabs.entities.Department;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Cliente de Open-Meteo (https://open-meteo.com): API de clima publica, gratuita y sin clave.
// Usa dos servicios: geocodificacion (nombre de la localidad -> coordenadas) y pronostico hora por hora.
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    // Hasta cuantos dias adelante da pronostico Open-Meteo (el maximo que permite)
    public static final int FORECAST_DAYS = 16;

    private final RestClient restClient;
    private final String forecastUrl;
    private final String geocodingUrl;

    public OpenMeteoWeatherClient(@Value("${app.weather.forecast-url}") String forecastUrl,
                                  @Value("${app.weather.geocoding-url}") String geocodingUrl,
                                  @Value("${app.weather.timeout-millis}") long timeoutMillis) {
        // Con timeout: si la API no responde, el detalle de la actividad no se puede quedar esperando
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.forecastUrl = forecastUrl;
        this.geocodingUrl = geocodingUrl;
    }

    // Busca la localidad en Uruguay y se queda con la del departamento correcto (hay nombres repetidos)
    @Override
    public Optional<Coordinates> geocode(String locationName, Department department) {
        GeocodingResponse response = restClient.get()
                .uri(geocodingUrl + "?name={name}&count=10&language=es&countryCode=UY", locationName)
                .retrieve().body(GeocodingResponse.class);
        return response == null || response.results() == null ? Optional.empty() : pick(response.results(), department);
    }

    static Optional<Coordinates> pick(List<Place> places, Department department) {
        String wanted = normalize(department.getLabel());
        return places.stream()
                .filter(place -> place.admin1() != null && normalize(place.admin1()).contains(wanted))
                .findFirst()
                .map(place -> new Coordinates(place.latitude(), place.longitude()));
    }

    @Override
    public Map<LocalDateTime, WeatherForecast> hourlyForecast(Coordinates coordinates) {
        ForecastResponse response = restClient.get()
                .uri(forecastUrl + "?latitude={latitude}&longitude={longitude}"
                                + "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m"
                                + "&timezone=America/Montevideo&forecast_days={days}",
                        coordinates.latitude(), coordinates.longitude(), FORECAST_DAYS)
                .retrieve().body(ForecastResponse.class);
        if (response == null || response.hourly() == null) {
            throw new IllegalStateException("Open-Meteo returned an empty forecast");
        }
        return toForecasts(response.hourly());
    }

    // Las listas de la respuesta van en paralelo (posicion i = la hora time[i]). Las horas sin dato se saltean.
    static Map<LocalDateTime, WeatherForecast> toForecasts(Hourly hourly) {
        Map<LocalDateTime, WeatherForecast> forecasts = new HashMap<>();
        for (int i = 0; i < hourly.time().size(); i++) {
            Double temperature = valueAt(hourly.temperature(), i);
            Integer code = valueAt(hourly.weatherCode(), i);
            if (temperature == null || code == null) {
                continue;
            }
            LocalDateTime time = LocalDateTime.parse(hourly.time().get(i));
            Double wind = valueAt(hourly.windSpeed(), i);
            forecasts.put(time, new WeatherForecast(time, (int) Math.round(temperature),
                    valueAt(hourly.rainProbability(), i), WeatherCondition.fromWmoCode(code),
                    wind == null ? 0 : (int) Math.round(wind)));
        }
        return forecasts;
    }

    private static <T> T valueAt(List<T> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    // Sin tildes y en minuscula: "Departamento de Paysandú" contiene "paysandu"
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    // ---------- Respuestas de la API ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeocodingResponse(List<Place> results) {
    }

    // admin1 es el departamento, ej. "Departamento de Rocha"
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String name, double latitude, double longitude, String admin1) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastResponse(Hourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hourly(List<String> time,
                  @JsonProperty("temperature_2m") List<Double> temperature,
                  @JsonProperty("precipitation_probability") List<Integer> rainProbability,
                  @JsonProperty("weather_code") List<Integer> weatherCode,
                  @JsonProperty("wind_speed_10m") List<Double> windSpeed) {
    }
}
