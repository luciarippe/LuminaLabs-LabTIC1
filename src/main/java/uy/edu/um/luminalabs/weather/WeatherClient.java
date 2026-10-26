package uy.edu.um.luminalabs.weather;

import uy.edu.um.luminalabs.entities.Department;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

// API externa de clima (RF-15). Cualquier problema (timeout, error, sin conexion) sale como excepcion:
// el que decide que hacer es WeatherService, que en ese caso muestra las paginas sin pronostico (CU-07 8a).
public interface WeatherClient {

    // Coordenadas de una localidad de Uruguay, o vacio si la API no la conoce
    Optional<Coordinates> geocode(String locationName, Department department);

    // Pronostico hora por hora de los proximos dias (clave: la hora, en horario de Uruguay)
    Map<LocalDateTime, WeatherForecast> hourlyForecast(Coordinates coordinates);
}
