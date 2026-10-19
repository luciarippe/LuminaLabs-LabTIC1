package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Bancos habilitados por el BCU y billeteras digitales donde un emprendimiento puede recibir los pagos.
// El formato de cada cuenta es el que pide el Sistema de Pagos Interbancarios
// (tabla del BROU: brou.com.uy/personas/servicios/spi).
// "firstDigits" vacio = la cuenta puede empezar con cualquier digito.
@Getter
@RequiredArgsConstructor
public enum Bank {
    // Bancos
    BROU("Banco República (BROU)", false, 14, 14, "",
            "14 dígitos: 9 de la cuenta y 5 de la subcuenta. Completá con ceros a la izquierda."),
    BHU("Banco Hipotecario del Uruguay (BHU)", false, 12, 12, "",
            "12 dígitos: sucursal, cuenta y dígito verificador, sin guiones."),
    BANDES("Bandes Uruguay", false, 9, 9, "",
            "9 dígitos: 3 de la sucursal y 6 de la cuenta."),
    BBVA("BBVA", false, 1, 9, "",
            "Hasta 9 dígitos, sin completar con ceros a la izquierda."),
    BTG_PACTUAL("BTG Pactual (ex HSBC)", false, 4, 10, "",
            "Entre 4 y 10 dígitos, sin completar con ceros a la izquierda."),
    CITIBANK("Citibank", false, 10, 10, "015",
            "10 dígitos; empieza con 0, 1 o 5."),
    HERITAGE("Banque Heritage", false, 9, 9, "",
            "9 dígitos: 7 de la cuenta y 2 de la subcuenta."),
    ITAU("Itaú", false, 7, 7, "",
            "7 dígitos. Completá con ceros a la izquierda."),
    NACION_ARGENTINA("Banco de la Nación Argentina", false, 1, 12, "",
            "Hasta 12 dígitos."),
    SANTANDER("Santander", false, 16, 16, "",
            "16 dígitos: 4 de la sucursal y 12 de la cuenta, con ceros a la izquierda."),
    SCOTIABANK("Scotiabank", false, 10, 10, "",
            "10 dígitos: 7 del número de cliente, 1 verificador y 2 de la cuenta."),

    // Billeteras digitales
    ANDA_DINERO_ELECTRONICO("Dinero Electrónico ANDA", true, 12, 12, "",
            "12 dígitos."),
    MERCADO_PAGO("Mercado Pago", true, 13, 13, "",
            "13 dígitos."),
    MIDINERO("MiDinero", true, 3, 11, "",
            "Entre 3 y 11 dígitos, sin completar con ceros a la izquierda."),
    OCA_BLUE("OCA Blue", true, 7, 7, "",
            "7 dígitos. Completá con ceros a la izquierda."),
    PREX("Prex", true, 1, 8, "",
            "Hasta 8 dígitos, sin completar con ceros a la izquierda.");

    private final String label;
    private final boolean wallet;
    private final int minDigits;
    private final int maxDigits;
    private final String firstDigits;
    private final String accountHint;

    // Mensaje para el usuario si el numero de cuenta no cumple el formato del banco; null si esta bien.
    // Se espera que ya venga validado como "solo digitos" (BusinessForm).
    public String accountNumberError(String accountNumber) {
        int digits = accountNumber.length();
        if (digits < minDigits || digits > maxDigits) {
            String expected = minDigits == maxDigits ? String.valueOf(maxDigits)
                    : minDigits == 1 ? "hasta " + maxDigits
                    : "entre " + minDigits + " y " + maxDigits;
            return "En " + label + " la cuenta tiene " + expected + " dígitos (ingresaste " + digits + ").";
        }
        if (!firstDigits.isEmpty() && firstDigits.indexOf(accountNumber.charAt(0)) < 0) {
            return "En " + label + " la cuenta empieza con " + String.join(", ", firstDigits.split(""))
                    .replaceFirst(", (\\d)$", " o $1") + ".";
        }
        return null;
    }
}
