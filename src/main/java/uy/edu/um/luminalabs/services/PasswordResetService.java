package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.PasswordResetForm;
import uy.edu.um.luminalabs.entities.PasswordResetToken;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.PasswordResetRequestedEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.PasswordResetTokenRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;
import uy.edu.um.luminalabs.security.UserSessionTerminator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

// Recuperar la contrasena olvidada (turistas, prestadores y administradores):
//  1. la persona escribe el correo de su cuenta y le mandamos un enlace (vence a la hora, sirve una vez);
//  2. desde el enlace elige una contrasena nueva; se cierran las sesiones que tuviera abiertas.
// La respuesta del paso 1 es siempre la misma, exista o no la cuenta: asi no se puede averiguar que correos
// estan registrados. Las cuentas bloqueadas no reciben enlace (igual no podrian ingresar).
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionTerminator sessionTerminator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(AccountValidator.normalizeEmail(email))
                .filter(user -> !user.isBlocked())
                .ifPresent(this::sendLink);
    }

    // Si ya tenia un enlace sin usar, se vence: solo sirve el del ultimo correo
    private void sendLink(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        tokenRepository.findByUser_IdAndUsedAtIsNullAndExpiresAtAfter(user.getId(), now)
                .forEach(previous -> previous.revoke(now));
        String token = SecureTokens.newToken();
        PasswordResetToken saved = tokenRepository.save(new PasswordResetToken(user, SecureTokens.hash(token), now));
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(MailRecipient.from(user), user.getUsername(),
                token, saved.getExpiresAt()));
    }

    // El enlace del correo, si todavia se puede usar
    @Transactional(readOnly = true)
    public PasswordResetToken findUsableToken(String token) {
        return tokenRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(found -> found.isUsable(LocalDateTime.now(clock)))
                .filter(found -> !found.getUser().isBlocked())
                .orElseThrow(() -> new IllegalStateException(
                        "Este enlace ya no es válido: puede que se haya usado o que haya vencido."));
    }

    // Devuelve la cuenta, para saludar a la persona en el aviso
    @Transactional
    public User resetPassword(String token, PasswordResetForm form) {
        PasswordResetToken resetToken = findUsableToken(token);
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new FieldValidationException(Map.of("confirmPassword", "Las contraseñas no coinciden."));
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        resetToken.use(LocalDateTime.now(clock));
        // Si alguien mas tenia la sesion abierta (por eso se cambia la contrasena), deja de tenerla
        sessionTerminator.terminateSessionsOf(user.getId());
        return user;
    }
}
