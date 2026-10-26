package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;

// CU-10 paso 5: nombre y departamento de una localidad
@Getter
@Setter
@NoArgsConstructor
public class LocationForm {

    @NotBlank(message = "Ingresá el nombre de la localidad.")
    @Size(max = 80, message = "Máximo 80 caracteres.")
    private String name;

    @NotNull(message = "Elegí el departamento.")
    private Department department;

    public LocationForm(Department department) {
        this.department = department;
    }

    public LocationForm(Location location) {
        this.name = location.getName();
        this.department = location.getDepartment();
    }
}
