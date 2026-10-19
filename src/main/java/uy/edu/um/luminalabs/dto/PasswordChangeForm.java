package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Cambio de contrasena de la propia cuenta: se pide la actual para confirmar que es el dueno
@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "Ingresá tu contraseña actual.")
    private String currentPassword;

    @NotBlank(message = "Elegí una contraseña nueva.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    private String newPassword;

    @NotBlank(message = "Repetí la contraseña nueva.")
    private String confirmPassword;
}
