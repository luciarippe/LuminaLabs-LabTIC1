package uy.edu.um.luminalabs.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.YearMonth;

// CU-01 paso 7 y CU-16 paso 3: datos de la tarjeta del turista. No se guardan: se mandan a la pasarela
// y de ellos solo quedan en la base el titular y los ultimos 4 digitos (ver Payment).
@Getter
@Setter
public class CardForm {

    @NotBlank(message = "Ingresá el nombre como figura en la tarjeta.")
    @Size(max = 100, message = "Máximo 100 caracteres.")
    private String cardHolder;

    // Se aceptan espacios o guiones entre los grupos de numeros
    @NotBlank(message = "Ingresá el número de tarjeta.")
    @Pattern(regexp = "^[0-9 -]{13,23}$", message = "El número de tarjeta no es válido.")
    private String cardNumber;

    @NotBlank(message = "Ingresá el vencimiento.")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Usá el formato MM/AA.")
    private String expiry;

    @NotBlank(message = "Ingresá el código de seguridad.")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "Son 3 o 4 números.")
    private String cvv;

    public String getCardDigits() {
        return cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
    }

    public String getLastDigits() {
        String digits = getCardDigits();
        return digits.length() < 4 ? digits : digits.substring(digits.length() - 4);
    }

    // Bean Validation trata este metodo como una propiedad "notExpired": el error queda en ese campo y pay.html
    // lo muestra debajo del vencimiento. Si el formato es invalido ya lo marca @Pattern, asi que aca se da por bueno.
    @AssertTrue(message = "La tarjeta está vencida.")
    public boolean isNotExpired() {
        if (expiry == null || !expiry.matches("^(0[1-9]|1[0-2])/[0-9]{2}$")) {
            return true;
        }
        YearMonth cardMonth = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)), Integer.parseInt(expiry.substring(0, 2)));
        return !cardMonth.isBefore(YearMonth.now());
    }
}
