package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.BusinessType;

import java.util.ArrayList;
import java.util.List;

// Datos del formulario de registro de emprendimiento (CU-02, pasos 2 a 4).
// Los datos de la cuenta del prestador vienen de AccountForm.
@Getter
@Setter
public class ProviderRegistrationForm extends AccountForm {

    // Emprendimiento
    @NotNull(message = "Elegí el tipo de emprendimiento.")
    private BusinessType businessType;

    @NotBlank(message = "Ingresá la razón social o el nombre.")
    @Size(max = 150, message = "Máximo 150 caracteres.")
    private String legalName;

    @NotBlank(message = "Ingresá el RUT o la cédula.")
    private String identifier;

    @NotBlank(message = "Contanos qué servicios ofrecés.")
    @Size(max = 2000, message = "Máximo 2000 caracteres.")
    private String description;

    @NotEmpty(message = "Elegí al menos una localidad.")
    private List<Long> locationIds = new ArrayList<>();

    // Contacto y datos bancarios
    @NotBlank(message = "Ingresá un correo de contacto.")
    @Email(message = "El correo no tiene un formato válido.")
    private String contactEmail;

    @NotBlank(message = "Ingresá un teléfono de contacto.")
    @Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "Ingresá un teléfono válido.")
    private String contactPhone;

    @NotBlank(message = "Ingresá el banco.")
    private String bankName;

    @NotBlank(message = "Ingresá el número de cuenta.")
    @Size(max = 40, message = "Máximo 40 caracteres.")
    private String bankAccountNumber;
}
