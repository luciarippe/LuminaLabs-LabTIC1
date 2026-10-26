package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.ProviderInvitation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProviderInvitationRepository extends JpaRepository<ProviderInvitation, Long> {

    // El enlace del correo: se busca por el hash del token, con el emprendimiento para mostrar su nombre
    @EntityGraph(attributePaths = {"business", "invitedBy"})
    Optional<ProviderInvitation> findByTokenHash(String tokenHash);

    // Invitaciones pendientes de un emprendimiento (no aceptadas y sin vencer), la mas nueva primero
    List<ProviderInvitation> findByBusiness_IdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long businessId, LocalDateTime now);
}
