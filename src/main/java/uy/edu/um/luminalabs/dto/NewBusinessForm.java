package uy.edu.um.luminalabs.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

// CU-02 1a: un prestador que ya inicio sesion agrega otro emprendimiento; no ingresa datos de cuenta.
// Envuelve a BusinessForm para que los campos se llamen igual que en el registro publico ("business.*")
// y las dos paginas compartan el mismo fragmento de formulario.
@Getter
@Setter
public class NewBusinessForm {

    @Valid
    private BusinessForm business = new BusinessForm();
}
