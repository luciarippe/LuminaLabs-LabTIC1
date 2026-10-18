package uy.edu.um.luminalabs.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

// Formulario de registro publico de emprendimiento (CU-02): los datos de la cuenta del prestador vienen de
// AccountForm y los del emprendimiento van anidados en "business". @Valid hace que tambien se validen esos.
@Getter
@Setter
public class ProviderRegistrationForm extends AccountForm {

    @Valid
    private BusinessForm business = new BusinessForm();
}
