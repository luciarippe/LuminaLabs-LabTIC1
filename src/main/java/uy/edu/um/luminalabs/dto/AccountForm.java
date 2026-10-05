package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Datos de una cuenta nueva, comunes al registro de turistas (CU-12) y de prestadores (CU-02)
@Getter
@Setter
public abstract class AccountForm {

    @NotBlank(message = "Ingresá tu nombre.")
    private String firstName;

    @NotBlank(message = "Ingresá tu apellido.")
    private String lastName;

    @NotNull(message = "Ingresá tu fecha de nacimiento.")
    @Past(message = "La fecha debe ser anterior a hoy.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    // Sin "@" para que nunca se confunda con un correo al iniciar sesion
    @NotBlank(message = "Elegí un nombre de usuario.")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,30}$",
            message = "Entre 3 y 30 caracteres: letras, números, punto, guion o guion bajo.")
    private String username;

    @NotBlank(message = "Ingresá tu correo.")
    @Email(message = "El correo no tiene un formato válido.")
    private String email;

    @NotBlank(message = "Elegí una contraseña.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    private String password;

    @NotBlank(message = "Repetí la contraseña.")
    private String confirmPassword;
}
