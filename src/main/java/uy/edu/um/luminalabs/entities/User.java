package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.experimental.SuperBuilder;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @NotBlank
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Email(message = "Formato de correo electronico no valido")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // Cuenta bloqueada por un administrador: no puede iniciar sesion. null = activa.
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    public abstract Role getRole();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isBlocked() {
        return blockedAt != null;
    }

    public void block(String reason, LocalDateTime now) {
        if (isBlocked()) {
            throw new IllegalStateException("La cuenta ya está bloqueada.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to block an account");
        }
        blockedAt = now;
        blockedReason = reason.strip();
    }

    public void unblock() {
        if (!isBlocked()) {
            throw new IllegalStateException("La cuenta no está bloqueada.");
        }
        blockedAt = null;
        blockedReason = null;
    }
}