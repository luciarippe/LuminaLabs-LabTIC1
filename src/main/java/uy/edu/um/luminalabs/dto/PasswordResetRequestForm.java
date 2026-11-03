package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Recuperar la contrasena, paso 1: el correo de la cuenta, para mandarle el enlace
@Getter
@Setter
public class PasswordResetRequestForm {

    @NotBlank(message = "Ingresá el correo de tu cuenta.")
    @Email(message = "El correo no tiene un formato válido.")
    private String email;
}
