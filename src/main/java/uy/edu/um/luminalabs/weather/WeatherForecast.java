package uy.edu.um.luminalabs.weather;

import java.time.LocalDateTime;

// RF-15: pronostico para una hora en un lugar. rainProbability puede ser null (la API no siempre lo da).
public record WeatherForecast(LocalDateTime time, int temperature, Integer rainProbability, WeatherCondition condition,
                              int windSpeed) {

    // Desde esta probabilidad de lluvia se avisa al turista
    public static final int RAIN_WARNING_PERCENT = 50;

    public boolean isRainLikely() {
        return condition.isWet() || (rainProbability != null && rainProbability >= RAIN_WARNING_PERCENT);
    }

    // Ej. "Parcialmente nublado, 22 °C, 10 % de probabilidad de lluvia"
    public String getSummary() {
        String summary = condition.getLabel() + ", " + temperature + " °C";
        return rainProbability == null ? summary : summary + ", " + rainProbability + " % de probabilidad de lluvia";
    }
}
