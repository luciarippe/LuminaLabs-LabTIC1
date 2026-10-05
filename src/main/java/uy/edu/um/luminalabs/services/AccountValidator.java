package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.dto.AccountForm;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.util.Locale;
import java.util.Map;

// Reglas de una cuenta nueva que dependen de la base de datos (RF-04: correo y usuario unicos)
@Component
@RequiredArgsConstructor
public class AccountValidator {

    private final UserRepository userRepository;

    // Agrega a "errors" los problemas encontrados (campo -> mensaje)
    public void validateNewAccount(AccountForm form, Map<String, String> errors) {
        if (userRepository.existsByUsernameIgnoreCase(form.getUsername().trim())) {
            errors.put("username", "Ese nombre de usuario ya está en uso. Elegí otro.");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizeEmail(form.getEmail()))) {
            errors.put("email", "Ya existe una cuenta con este correo.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            errors.put("confirmPassword", "Las contraseñas no coinciden.");
        }
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
