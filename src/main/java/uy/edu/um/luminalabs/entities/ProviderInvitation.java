package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// CU-15 4a: invitacion a alguien sin cuenta para sumarse como prestador de un emprendimiento. Se acepta una sola
// vez y vence a los VALIDITY. Del enlace se guarda solo el hash (ver ProviderTeamService).
@Entity
@Table(name = "provider_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderInvitation {

    public static final Duration VALIDITY = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private Provider invitedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // null mientras no se acepto
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    public ProviderInvitation(Business business, String email, String tokenHash, Provider invitedBy, LocalDateTime now) {
        this.business = business;
        this.email = email;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    // Se puede usar: no se acepto, no vencio y no se cancelo
    public boolean isPending(LocalDateTime now) {
        return acceptedAt == null && now.isBefore(expiresAt);
    }

    public void accept(LocalDateTime now) {
        if (!isPending(now)) {
            throw new IllegalStateException("La invitación ya se usó o venció.");
        }
        acceptedAt = now;
    }

    // Cancelada por un prestador del emprendimiento (o reemplazada por una nueva al mismo correo): vence ya
    public void revoke(LocalDateTime now) {
        if (isPending(now)) {
            expiresAt = now;
        }
    }
}
