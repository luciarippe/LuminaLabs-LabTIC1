package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.PasswordChangeForm;
import uy.edu.um.luminalabs.dto.ProfileForm;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// "Mi cuenta": cada usuario ve y cambia sus propios datos y su contrasena (turistas y administradores)
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " not found"));
    }

    // Formulario de "Mis datos" con los valores actuales
    @Transactional(readOnly = true)
    public ProfileForm profileOf(Long userId) {
        User user = findUser(userId);
        ProfileForm form = new ProfileForm();
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setBirthDate(user.getBirthDate());
        form.setEmail(user.getEmail());
        return form;
    }

    @Transactional
    public User updateProfile(Long userId, ProfileForm form) {
        User user = findUser(userId);
        String email = AccountValidator.normalizeEmail(form.getEmail());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new FieldValidationException(Map.of("email", "Ya existe una cuenta con este correo."));
        }
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setBirthDate(form.getBirthDate());
        user.setEmail(email);
        return user;
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeForm form) {
        User user = findUser(userId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            errors.put("currentPassword", "La contraseña actual no es correcta.");
        } else if (form.getNewPassword().equals(form.getCurrentPassword())) {
            errors.put("newPassword", "La contraseña nueva tiene que ser distinta de la actual.");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            errors.put("confirmPassword", "Las contraseñas no coinciden.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
    }
}
