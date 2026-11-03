package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// Enlace para elegir una contrasena nueva cuando se olvido la actual. Se usa una sola vez y vence a los VALIDITY.
// Del enlace se guarda solo el hash (ver PasswordResetService).
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    public static final Duration VALIDITY = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // null mientras no se uso
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public PasswordResetToken(User user, String tokenHash, LocalDateTime now) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    // Se puede usar: no se uso y no vencio
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("El enlace ya se usó o venció.");
        }
        usedAt = now;
    }

    // Reemplazado por un enlace nuevo: vence ya
    public void revoke(LocalDateTime now) {
        if (isUsable(now)) {
            expiresAt = now;
        }
    }
}
