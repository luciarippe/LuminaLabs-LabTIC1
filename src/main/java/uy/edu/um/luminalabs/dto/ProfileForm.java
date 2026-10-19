package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Datos personales que un usuario puede cambiar de su propia cuenta. El nombre de usuario no se cambia
// (es con lo que inicia sesion) y la contrasena se cambia aparte (PasswordChangeForm).
// Mismas reglas que AccountForm.
@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "Ingresá tu nombre.")
    @Size(max = 60, message = "Máximo 60 caracteres.")
    @Pattern(regexp = AccountForm.NAME_PATTERN, message = AccountForm.NAME_MESSAGE)
    private String firstName;

    @NotBlank(message = "Ingresá tu apellido.")
    @Size(max = 60, message = "Máximo 60 caracteres.")
    @Pattern(regexp = AccountForm.NAME_PATTERN, message = AccountForm.NAME_MESSAGE)
    private String lastName;

    @NotNull(message = "Ingresá tu fecha de nacimiento.")
    @Past(message = "La fecha debe ser anterior a hoy.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    @NotBlank(message = "Ingresá tu correo.")
    @Email(message = "El correo no tiene un formato válido.")
    private String email;
}
