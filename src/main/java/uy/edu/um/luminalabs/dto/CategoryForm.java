package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.ActivityCategory;

// CU-10 paso 5: nombre y descripcion de una categoria de actividad
@Getter
@Setter
@NoArgsConstructor
public class CategoryForm {

    @NotBlank(message = "Ingresá el nombre de la categoría.")
    @Size(max = 60, message = "Máximo 60 caracteres.")
    private String name;

    @Size(max = 500, message = "Máximo 500 caracteres.")
    private String description;

    public CategoryForm(ActivityCategory category) {
        this.name = category.getName();
        this.description = category.getDescription();
    }
}
