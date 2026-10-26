package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// CU-15 paso 3: el correo de la persona que se quiere sumar como prestador
@Getter
@Setter
@NoArgsConstructor
public class AddProviderForm {

    @NotBlank(message = "Ingresá el correo de la persona.")
    @Email(message = "El correo no tiene un formato válido.")
    private String email;
}
