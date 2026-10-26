package uy.edu.um.luminalabs.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Lo que devuelve la API publica (RF-25) en JSON. Son copias de solo lectura de las entidades, con lo que es
// publico: por ejemplo, de un emprendimiento no se devuelven su cuenta bancaria ni sus datos de contacto internos.
public final class ApiDtos {

    private ApiDtos() {
    }

    @Schema(description = "Departamento: su código (para usar en los parámetros) y su nombre")
    public record DepartmentDto(@Schema(example = "ROCHA") String code, @Schema(example = "Rocha") String name) {
    }

    @Schema(description = "Localidad")
    public record LocationDto(@Schema(example = "21") Long id, @Schema(example = "La Paloma") String name,
                              DepartmentDto department) {
    }

    @Schema(description = "Emprendimiento aprobado")
    public record BusinessDto(@Schema(example = "219999990011", description = "RUT o cédula, solo números") String identifier,
                              @Schema(example = "Costa Este Experiencias") String name,
                              @Schema(example = "Empresa") String type,
                              String description,
                              @Schema(description = "Localidades donde opera") List<LocationDto> locations,
                              @Schema(description = "Sitio web y redes sociales (null si no tiene)") SocialLinksDto links) {
    }

    public record SocialLinksDto(String website, String instagram, String facebook, String tiktok) {
    }

    @Schema(description = "Actividad publicada")
    public record ActivityDto(@Schema(example = "1") Long id,
                              @Schema(example = "Kayak al atardecer en la Laguna de Rocha") String name,
                              String description,
                              @Schema(example = "Costa Este Experiencias") String business,
                              LocationDto location,
                              @Schema(example = "[\"Actividades acuáticas\", \"Turismo de naturaleza\"]") List<String> categories,
                              @Schema(example = "150") int durationMinutes,
                              @Schema(example = "Español, inglés") String language,
                              @Schema(example = "12", description = "null = apta para todas las edades") Integer minimumAge,
                              @Schema(example = "http://localhost:8080/activities/1", description = "Página de la actividad en el portal") String url) {
    }

    @Schema(description = "Horario de una actividad")
    public record SlotDto(@Schema(example = "2026-11-01T17:30:00") LocalDateTime start,
                          @Schema(example = "1900.00", description = "Precio por persona, en pesos uruguayos") BigDecimal price,
                          @Schema(example = "8") int availableSeats,
                          @Schema(example = "12") int capacity) {
    }

    @Schema(description = "Valoración de una actividad: promedio de estrellas (1 a 5) y cantidad de valoraciones")
    public record RatingDto(@Schema(example = "4.5") double average, @Schema(example = "8") long count) {
    }

    @Schema(description = "Resultado de búsqueda")
    public record ActivitySearchResultDto(ActivityDto activity,
                                          @Schema(description = "Precio por persona más bajo de los próximos horarios (null si no tiene fechas)") BigDecimal fromPrice,
                                          @Schema(description = "Próximo horario (null si no tiene fechas)") LocalDateTime nextStart,
                                          @Schema(description = "null si no tiene valoraciones") RatingDto rating) {
    }

    @Schema(description = "Actividad con sus horarios del día consultado")
    public record ActivityOfDayDto(ActivityDto activity, List<SlotDto> slots) {
    }

    @Schema(description = "Actividades de un día en un lugar")
    public record ActivitiesOfDayDto(@Schema(example = "2026-11-01") LocalDate date,
                                     @Schema(description = "El departamento consultado (null si se consultó por localidad)") DepartmentDto department,
                                     @Schema(description = "La localidad consultada (null si se consultó por departamento)") LocationDto location,
                                     List<ActivityOfDayDto> activities) {
    }

    @Schema(description = "Una actividad del ranking")
    public record RankedActivityDto(@Schema(example = "1") int position, ActivityDto activity, RatingDto rating) {
    }

    @Schema(description = "Las 3 actividades mejor valoradas del mes en un departamento")
    public record TopActivitiesDto(DepartmentDto department, @Schema(example = "10") int month,
                                   @Schema(example = "2026") int year, List<RankedActivityDto> activities) {
    }

    @Schema(description = "Verificación de un emprendimiento")
    public record VerificationDto(@Schema(example = "219999990011") String identifier,
                                  @Schema(description = "true si existe y está aprobado") boolean verified,
                                  @Schema(description = "El emprendimiento, solo si está verificado") BusinessDto business) {
    }

    @Schema(description = "Error: parámetros faltantes o inválidos")
    public record ApiError(@Schema(example = "400") int status, @Schema(example = "Bad Request") String error,
                           @Schema(example = "Falta el parámetro 'date' (fecha en formato AAAA-MM-DD).") String message,
                           @Schema(example = "/api/v1/activities/day") String path) {
    }
}
