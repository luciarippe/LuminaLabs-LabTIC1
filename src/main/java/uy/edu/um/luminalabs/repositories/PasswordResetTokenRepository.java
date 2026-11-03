package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // El enlace del correo: se busca por el hash del token, con la cuenta a la que pertenece
    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Enlaces de una cuenta que todavia se pueden usar (al pedir uno nuevo, los anteriores se vencen)
    List<PasswordResetToken> findByUser_IdAndUsedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);
}
