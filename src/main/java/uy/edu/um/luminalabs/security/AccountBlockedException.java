package uy.edu.um.luminalabs.security;

import org.springframework.security.authentication.AccountStatusException;

// Credenciales correctas, pero un administrador bloqueo la cuenta (ver User.block)
public class AccountBlockedException extends AccountStatusException {

    public AccountBlockedException() {
        super("Account blocked by an administrator");
    }
}
