package uy.edu.um.luminalabs.weather;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Estado del tiempo a partir del codigo WMO que devuelve la API (https://open-meteo.com/en/docs, "WMO Weather
// interpretation codes"). "icon" es el nombre del icono en fragments/icons.
@Getter
@RequiredArgsConstructor
public enum WeatherCondition {
    CLEAR("Despejado", "sun"),
    PARTLY_CLOUDY("Parcialmente nublado", "cloud-sun"),
    CLOUDY("Nublado", "cloud"),
    FOG("Niebla", "fog"),
    DRIZZLE("Llovizna", "rain"),
    RAIN("Lluvia", "rain"),
    HEAVY_RAIN("Lluvia fuerte", "rain"),
    SHOWERS("Chaparrones", "rain"),
    SNOW("Nieve", "snow"),
    STORM("Tormenta", "storm");

    private final String label;
    private final String icon;

    public static WeatherCondition fromWmoCode(int code) {
        return switch (code) {
            case 0, 1 -> CLEAR;
            case 2 -> PARTLY_CLOUDY;
            case 3 -> CLOUDY;
            case 45, 48 -> FOG;
            case 51, 53, 55, 56, 57 -> DRIZZLE;
            case 61, 63, 66 -> RAIN;
            case 65, 67 -> HEAVY_RAIN;
            case 80, 81, 82 -> SHOWERS;
            case 71, 73, 75, 77, 85, 86 -> SNOW;
            case 95, 96, 99 -> STORM;
            default -> CLOUDY;
        };
    }

    // Para avisar que conviene llevar abrigo para la lluvia o que la actividad puede verse afectada
    public boolean isWet() {
        return this == DRIZZLE || this == RAIN || this == HEAVY_RAIN || this == SHOWERS || this == STORM;
    }
}
