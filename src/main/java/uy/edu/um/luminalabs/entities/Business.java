package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
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

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

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

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addProvider(Provider provider) {
        providers.add(provider);
        provider.getBusinesses().add(this);
    }

    public void approve() {
        requireStatus(BusinessStatus.PENDING, "Solo se pueden aprobar emprendimientos pendientes.");
        changeStatus(BusinessStatus.APPROVED, null);
    }

    public void deny(String reason) {
        requireStatus(BusinessStatus.PENDING, "Solo se pueden denegar emprendimientos pendientes.");
        changeStatus(BusinessStatus.DENIED, reason);
    }

    public void suspend(String reason) {
        requireStatus(BusinessStatus.APPROVED, "Solo se pueden suspender emprendimientos aprobados.");
        changeStatus(BusinessStatus.SUSPENDED, reason);
    }

    public void reactivate() {
        requireStatus(BusinessStatus.SUSPENDED, "Solo se pueden reactivar emprendimientos suspendidos.");
        changeStatus(BusinessStatus.APPROVED, null);
    }

    private void requireStatus(BusinessStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void changeStatus(BusinessStatus newStatus, String reason) {
        status = newStatus;
        statusReason = reason;
        reviewedAt = LocalDateTime.now();
    }
}
