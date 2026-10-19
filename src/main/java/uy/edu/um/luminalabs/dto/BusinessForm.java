package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.Bank;
import uy.edu.um.luminalabs.entities.BusinessType;

import java.util.ArrayList;
import java.util.List;

// Datos de un emprendimiento (CU-02, pasos 2 y 3). Se usa anidado ("business.legalName", etc.) tanto en el
// registro publico (ProviderRegistrationForm) como cuando un prestador ya logueado agrega otro (NewBusinessForm).
@Getter
@Setter
public class BusinessForm {

    @NotNull(message = "Elegí el tipo de emprendimiento.")
    private BusinessType businessType;

    @NotBlank(message = "Ingresá la razón social o el nombre.")
    @Size(max = 150, message = "Máximo 150 caracteres.")
    private String legalName;

    // Los digitos (12 para RUT, 7 u 8 para cedula) los controla ProviderRegistrationService segun el tipo
    @NotBlank(message = "Ingresá el RUT o la cédula.")
    @Pattern(regexp = "^[0-9]*$", message = "Solo puede tener números, sin espacios, puntos ni guiones.")
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
    @Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "Entre 6 y 20 caracteres: números, espacios, +, paréntesis o guiones.")
    private String contactPhone;

    @NotNull(message = "Elegí el banco o la billetera.")
    private Bank bank;

    @NotBlank(message = "Ingresá el número de cuenta.")
    // La cantidad de digitos depende del banco: la controla ProviderRegistrationService con Bank.accountNumberError
    @Pattern(regexp = "^[0-9]*$", message = "Solo puede tener números, sin espacios, puntos ni guiones.")
    private String bankAccountNumber;

    // Opcionales: "business.social.instagram", etc.
    private SocialLinksForm social = new SocialLinksForm();
}
