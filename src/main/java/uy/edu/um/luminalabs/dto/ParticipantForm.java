package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// CU-01 paso 3: datos de cada participante
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantForm {

    @NotBlank(message = "Ingresá el nombre.")
    @Size(max = 100, message = "Máximo 100 caracteres.")
    private String firstName;

    @NotBlank(message = "Ingresá el apellido.")
    @Size(max = 100, message = "Máximo 100 caracteres.")
    private String lastName;

    @NotNull(message = "Ingresá la fecha de nacimiento.")
    @Past(message = "La fecha tiene que ser anterior a hoy.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;
}
