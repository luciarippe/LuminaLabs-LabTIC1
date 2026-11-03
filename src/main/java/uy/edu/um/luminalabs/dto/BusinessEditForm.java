package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.Bank;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Location;

import java.util.ArrayList;
import java.util.List;

// Corregir los datos de un emprendimiento ya registrado (ver BusinessEditService). Son los de BusinessForm salvo el
// tipo y el RUT o cedula, que identifican al emprendimiento y los verifico el administrador, y las redes sociales,
// que tienen su propia pantalla.
@Getter
@Setter
@NoArgsConstructor
public class BusinessEditForm {

    @NotBlank(message = "Ingresá la razón social o el nombre.")
    @Size(max = 150, message = "Máximo 150 caracteres.")
    private String legalName;

    @NotBlank(message = "Contanos qué servicios ofrecés.")
    @Size(max = 2000, message = "Máximo 2000 caracteres.")
    private String description;

    @NotEmpty(message = "Elegí al menos una localidad.")
    private List<Long> locationIds = new ArrayList<>();

    @NotBlank(message = "Ingresá un correo de contacto.")
    @Email(message = "El correo no tiene un formato válido.")
    private String contactEmail;

    @NotBlank(message = "Ingresá un teléfono de contacto.")
    @Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "Entre 6 y 20 caracteres: números, espacios, +, paréntesis o guiones.")
    private String contactPhone;

    @NotNull(message = "Elegí el banco o la billetera.")
    private Bank bank;

    @NotBlank(message = "Ingresá el número de cuenta.")
    // La cantidad de digitos depende del banco: la controla BusinessEditService con Bank.accountNumberError
    @Pattern(regexp = "^[0-9]*$", message = "Solo puede tener números, sin espacios, puntos ni guiones.")
    private String bankAccountNumber;

    // Los datos actuales, para empezar a editar desde ahi
    public BusinessEditForm(Business business) {
        this.legalName = business.getLegalName();
        this.description = business.getDescription();
        this.locationIds = new ArrayList<>(business.getLocations().stream().map(Location::getId).toList());
        this.contactEmail = business.getContactEmail();
        this.contactPhone = business.getContactPhone();
        this.bank = business.getBank();
        this.bankAccountNumber = business.getBankAccountNumber();
    }
}
