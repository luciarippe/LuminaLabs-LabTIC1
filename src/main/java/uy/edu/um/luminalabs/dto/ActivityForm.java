package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// CU-03: datos de una actividad, para crearla o modificarla (RF-07 a RF-10).
// Los numeros son Integer (no int) para que un campo vacio llegue como null y lo marque @NotNull.
@Getter
@Setter
public class ActivityForm {

    // 1a: si el prestador gestiona varios emprendimientos, elige a cual pertenece. No se cambia al editar.
    @NotNull(message = "Elegí el emprendimiento.")
    private Long businessId;

    @NotBlank(message = "Ingresá el nombre de la actividad.")
    @Size(max = 150, message = "Máximo 150 caracteres.")
    private String name;

    @NotBlank(message = "Describí la experiencia.")
    @Size(max = 4000, message = "Máximo 4000 caracteres.")
    private String description;

    @NotNull(message = "Elegí la localidad.")
    private Long locationId;

    @NotBlank(message = "Ingresá la dirección o el punto de encuentro.")
    @Size(max = 255, message = "Máximo 255 caracteres.")
    private String address;

    // Los tiempos se eligen en horas, de a 30 minutos (HalfHourOption), pero se guardan en minutos
    public static final int MIN_DURATION_MINUTES = 30;
    public static final int MAX_DURATION_MINUTES = 24 * 60;
    public static final int MAX_NOTICE_MINUTES = 7 * 24 * 60;

    @NotNull(message = "Elegí la duración.")
    @Min(value = MIN_DURATION_MINUTES, message = "La duración mínima es de 0,5 h.")
    @Max(value = MAX_DURATION_MINUTES, message = "La duración máxima es de 24 h.")
    private Integer durationMinutes;

    @NotBlank(message = "Indicá el idioma o los idiomas.")
    @Size(max = 255, message = "Máximo 255 caracteres.")
    private String language;

    // Vacio = sin requisito de edad
    @Min(value = 1, message = "Indicá una edad entre 1 y 99, o dejalo vacío si no hay requisito.")
    @Max(value = 99, message = "Indicá una edad entre 1 y 99, o dejalo vacío si no hay requisito.")
    private Integer minimumAge;

    @Size(max = 1000, message = "Máximo 1000 caracteres.")
    private String accessibility;

    @Size(max = 2000, message = "Máximo 2000 caracteres.")
    private String inclusions;

    // RF-09
    @Size(max = 1000, message = "Máximo 1000 caracteres.")
    private String equipment;

    @Size(max = 2000, message = "Máximo 2000 caracteres.")
    private String importantDetails;

    // RF-08
    @NotEmpty(message = "Elegí al menos una categoría.")
    private List<Long> categoryIds = new ArrayList<>();

    // RF-10
    @NotNull(message = "Elegí hasta cuántas horas antes se puede reservar (0 h = hasta la hora de inicio).")
    @Min(value = 0, message = "No puede ser negativo.")
    @Max(value = MAX_NOTICE_MINUTES, message = "Máximo 168 h (una semana).")
    private Integer bookingCutoffMinutes;

    @NotNull(message = "Elegí el plazo de cancelación.")
    @Min(value = 0, message = "No puede ser negativo.")
    @Max(value = MAX_NOTICE_MINUTES, message = "Máximo 168 h (una semana).")
    private Integer cancellationDeadlineMinutes;

    @NotNull(message = "Indicá el porcentaje de cargo.")
    @Min(value = 0, message = "Entre 0 y 100.")
    @Max(value = 100, message = "Entre 0 y 100.")
    private Integer cancellationFeePercentage;

    // Fotos al editar: las que se quitan y la que pasa a ser portada (las nuevas llegan aparte, como archivos)
    private List<Long> removePhotoIds = new ArrayList<>();
    private Long coverPhotoId;
}
