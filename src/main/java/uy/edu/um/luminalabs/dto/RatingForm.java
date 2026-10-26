package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// CU-08 paso 3: puntuacion de 1 a 5 estrellas, un titular y la resena
@Getter
@Setter
@NoArgsConstructor
public class RatingForm {

    @NotNull(message = "Elegí de 1 a 5 estrellas.")
    @Min(value = 1, message = "Elegí de 1 a 5 estrellas.")
    @Max(value = 5, message = "Elegí de 1 a 5 estrellas.")
    private Integer stars;

    @NotBlank(message = "Escribí un titular.")
    @Size(max = 100, message = "Máximo 100 caracteres.")
    private String title;

    @NotBlank(message = "Contanos cómo fue tu experiencia.")
    @Size(max = 1000, message = "Máximo 1000 caracteres.")
    private String comment;
}
