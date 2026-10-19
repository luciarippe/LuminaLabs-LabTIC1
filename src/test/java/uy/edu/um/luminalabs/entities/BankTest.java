package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BankTest {

    @Test
    void acceptsAccountWithExactLength() {
        assertNull(Bank.BROU.accountNumberError("00123456700001"));
        assertNull(Bank.ITAU.accountNumberError("0123456"));
        assertNull(Bank.SANTANDER.accountNumberError("0001000123456789"));
    }

    @Test
    void rejectsAccountWithWrongExactLength() {
        assertEquals("En Banco República (BROU) la cuenta tiene 14 dígitos (ingresaste 9).",
                Bank.BROU.accountNumberError("001234567"));
    }

    @Test
    void acceptsAccountsWithinRange() {
        assertNull(Bank.BBVA.accountNumberError("1"));
        assertNull(Bank.BBVA.accountNumberError("123456789"));
        assertNull(Bank.BTG_PACTUAL.accountNumberError("1234"));
    }

    @Test
    void rejectsAccountsOutsideRange() {
        assertEquals("En BBVA la cuenta tiene hasta 9 dígitos (ingresaste 10).",
                Bank.BBVA.accountNumberError("1234567890"));
        assertEquals("En BTG Pactual (ex HSBC) la cuenta tiene entre 4 y 10 dígitos (ingresaste 3).",
                Bank.BTG_PACTUAL.accountNumberError("123"));
    }

    @Test
    void validatesWalletAccounts() {
        assertNull(Bank.MERCADO_PAGO.accountNumberError("1234567890123"));
        assertEquals("En Prex la cuenta tiene hasta 8 dígitos (ingresaste 9).",
                Bank.PREX.accountNumberError("123456789"));
        assertEquals("En MiDinero la cuenta tiene entre 3 y 11 dígitos (ingresaste 2).",
                Bank.MIDINERO.accountNumberError("12"));
    }

    @Test
    void checksFirstDigitWhenTheBankRequiresIt() {
        assertNull(Bank.CITIBANK.accountNumberError("5123456789"));
        assertEquals("En Citibank la cuenta empieza con 0, 1 o 5.",
                Bank.CITIBANK.accountNumberError("2123456789"));
    }
}
