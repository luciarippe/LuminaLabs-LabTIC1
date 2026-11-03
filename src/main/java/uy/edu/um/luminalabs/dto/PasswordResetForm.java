package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Recuperar la contrasena, paso 2: la contrasena nueva, elegida desde el enlace del correo
@Getter
@Setter
public class PasswordResetForm {

    @NotBlank(message = "Elegí una contraseña nueva.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    private String newPassword;

    @NotBlank(message = "Repetí la contraseña nueva.")
    private String confirmPassword;
}
