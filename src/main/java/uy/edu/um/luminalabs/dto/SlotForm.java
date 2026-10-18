package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// CU-04: fecha y hora, precio por persona y capacidad de un slot (RF-11)
@Getter
@Setter
public class SlotForm {

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
}
