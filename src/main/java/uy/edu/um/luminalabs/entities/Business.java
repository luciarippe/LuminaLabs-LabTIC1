package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Emprendimiento (RF-01). Los cambios de estado se hacen solo con los metodos de abajo,
// asi las transiciones invalidas (ej. aprobar uno ya denegado) no pueden ocurrir.
@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BusinessType type;

    // Razon social (empresa) o nombre (particular)
    @Column(name = "legal_name", nullable = false)
    private String legalName;

    // RUT o cedula, guardado solo con digitos
    @Column(name = "identifier", nullable = false, unique = true)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank", nullable = false)
    private Bank bank;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    // Redes sociales (opcionales), como direcciones completas armadas por SocialNetwork.toUrl
    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "tiktok_url")
    private String tiktokUrl;

    public boolean hasSocialLinks() {
        return websiteUrl != null || instagramUrl != null || facebookUrl != null || tiktokUrl != null;
    }

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BusinessStatus status = BusinessStatus.PENDING;

    // Motivo del rechazo o de la suspension
    @Setter(AccessLevel.NONE)
    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToMany
    @JoinTable(name = "business_locations",
            joinColumns = @JoinColumn(name = "business_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id"))
    @OrderBy("name ASC")
    private Set<Location> locations = new HashSet<>();

    // RF-03: un emprendimiento puede tener varios prestadores y viceversa
    @ManyToMany
    @JoinTable(name = "business_providers",
            joinColumns = @JoinColumn(name = "business_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_id"))
    private Set<Provider> providers = new HashSet<>();

    // Los servicios la fijan con el Clock de la aplicacion; esto es solo un respaldo
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addProvider(Provider provider) {
        providers.add(provider);
        provider.getBusinesses().add(this);
    }

    // CU-15 3a y 3b: se puede quitar a un prestador, pero el emprendimiento siempre conserva al menos uno
    public void removeProvider(Provider provider) {
        if (!hasProvider(provider.getId())) {
            throw new IllegalStateException("Esa persona no es prestador de este emprendimiento.");
        }
        if (providers.size() <= 1) {
            throw new IllegalStateException("El emprendimiento tiene que conservar al menos un prestador.");
        }
        providers.removeIf(current -> Objects.equals(current.getId(), provider.getId()));
        provider.getBusinesses().remove(this);
    }

    public boolean hasProvider(Long providerId) {
        return providers.stream().anyMatch(provider -> Objects.equals(provider.getId(), providerId));
    }

    public boolean isApproved() {
        return status == BusinessStatus.APPROVED;
    }

    // "now" lo pasa el servicio, tomado del Clock de la aplicacion, para poder fijar la hora en los tests
    public void approve(LocalDateTime now) {
        requireStatus(BusinessStatus.PENDING, "Solo se pueden aprobar emprendimientos pendientes.");
        changeStatus(BusinessStatus.APPROVED, null, now);
    }

    public void deny(String reason, LocalDateTime now) {
        requireStatus(BusinessStatus.PENDING, "Solo se pueden denegar emprendimientos pendientes.");
        changeStatus(BusinessStatus.DENIED, reason, now);
    }

    public void suspend(String reason, LocalDateTime now) {
        requireStatus(BusinessStatus.APPROVED, "Solo se pueden suspender emprendimientos aprobados.");
        changeStatus(BusinessStatus.SUSPENDED, reason, now);
    }

    public void reactivate(LocalDateTime now) {
        requireStatus(BusinessStatus.SUSPENDED, "Solo se pueden reactivar emprendimientos suspendidos.");
        changeStatus(BusinessStatus.APPROVED, null, now);
    }

    // CU-02 5c: un emprendimiento denegado se vuelve a postular con los datos corregidos.
    // Queda pendiente y sin revisar, como una solicitud nueva.
    public void resubmit() {
        requireStatus(BusinessStatus.DENIED, "Solo se pueden volver a postular emprendimientos denegados.");
        status = BusinessStatus.PENDING;
        statusReason = null;
        reviewedAt = null;
    }

    private void requireStatus(BusinessStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void changeStatus(BusinessStatus newStatus, String reason, LocalDateTime now) {
        status = newStatus;
        statusReason = reason;
        reviewedAt = now;
    }
}
