package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// CU-04: fecha y hora, precio por persona y capacidad de un slot (RF-11).
// Al agregar, el horario se puede repetir (repetition, repeatUntil, weekdays); al modificar uno se ignora.
@Getter
@Setter
public class SlotForm {

    // Hasta cuanto tiempo adelante se pueden cargar horarios, y fecha final sugerida al repetir
    public static final int MAX_MONTHS_AHEAD = 6;
    public static final int SUGGESTED_REPEAT_MONTHS = 3;
    // Tope de horarios creados de una vez (todos los dias durante 6 meses son ~183)
    public static final int MAX_SLOTS_PER_REPETITION = 200;

    @NotNull(message = "Elegí la fecha.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Elegí la hora.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime time;

    @NotNull(message = "Ingresá el precio por persona.")
    @Positive(message = "El precio tiene que ser mayor a cero.")
    @Digits(integer = 8, fraction = 2, message = "Hasta 8 cifras y 2 decimales.")
    private BigDecimal price;

    @NotNull(message = "Ingresá la capacidad.")
    @Positive(message = "La capacidad tiene que ser mayor a cero.")
    @Max(value = 10000, message = "Máximo 10000 personas.")
    private Integer maxCapacity;

    private SlotRepetition repetition = SlotRepetition.NONE;

    // Ultimo dia en que se repite (inclusive); obligatorio si se repite, lo controla ProviderSlotService
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatUntil;

    // Solo para SlotRepetition.WEEKDAYS
    private List<DayOfWeek> weekdays = new ArrayList<>();

    public boolean isRepeated() {
        return repetition != null && repetition != SlotRepetition.NONE;
    }
}
