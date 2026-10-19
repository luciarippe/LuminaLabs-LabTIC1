package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ACTIVITY del MER (RF-07 a RF-10)
@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    // Direccion o punto de encuentro
    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "language", nullable = false)
    private String language;

    // null = sin requisito de edad
    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "accessibility", length = 1000)
    private String accessibility;

    @Column(name = "inclusions", length = 2000)
    private String inclusions;

    @Column(name = "equipment", length = 1000)
    private String equipment;

    @Column(name = "important_details", length = 2000)
    private String importantDetails;

    // RF-10: minutos previos al inicio hasta los que se admite reservar
    @Column(name = "booking_cutoff_minutes", nullable = false)
    private int bookingCutoffMinutes;

    // RF-10: politica de cancelacion
    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours;

    @Column(name = "cancellation_fee_percentage", nullable = false)
    private int cancellationFeePercentage;

    @ManyToMany
    @JoinTable(name = "activity_category_links",
            joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @OrderBy("name ASC")
    private Set<ActivityCategory> categories = new HashSet<>();

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL)
    @OrderBy("startTime ASC")
    private List<Slot> slots = new ArrayList<>();

    // Galeria de fotos; la primera es la portada. BatchSize: en el catalogo se cargan las de varias actividades
    // juntas en vez de una consulta por tarjeta.
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<ActivityPhoto> photos = new ArrayList<>();

    public static final int MAX_PHOTOS = 8;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Los servicios la fijan con el Clock de la aplicacion; esto es solo un respaldo
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Slot addSlot(Slot slot) {
        slots.add(slot);
        return slot;
    }

    // ---------- Fotos ----------

    public void addPhoto(StoredImage image) {
        if (photos.size() >= MAX_PHOTOS) {
            throw new IllegalStateException("Una actividad puede tener hasta " + MAX_PHOTOS + " fotos.");
        }
        photos.add(new ActivityPhoto(this, image, photos.size()));
    }

    // Quita las fotos de la actividad con esos ids (las de otra actividad se ignoran) y reordena
    public void removePhotos(Collection<Long> photoIds) {
        photos.removeIf(photo -> photoIds.contains(photo.getId()));
        renumberPhotos();
    }

    // La foto elegida pasa a ser la portada (primera); si no es de esta actividad no cambia nada
    public void makeCover(Long photoId) {
        photos.stream().filter(photo -> photo.getId() != null && photo.getId().equals(photoId)).findFirst()
                .ifPresent(cover -> {
                    photos.remove(cover);
                    photos.addFirst(cover);
                    renumberPhotos();
                });
    }

    private void renumberPhotos() {
        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setPosition(i);
        }
    }

    // Imagen de portada: la primera foto, o la de la localidad si la actividad no tiene; null si ninguna tiene
    public Long getCoverImageId() {
        if (!photos.isEmpty()) {
            return photos.getFirst().getImageId();
        }
        return location == null ? null : location.getPhotoId();
    }

    // CU-01 3a: la edad se cuenta al dia de la actividad, no al dia en que se reserva
    public boolean allowsParticipantBornOn(LocalDate birthDate, LocalDate activityDate) {
        return minimumAge == null || Period.between(birthDate, activityDate).getYears() >= minimumAge;
    }

    // RNF-10: un prestador solo gestiona las actividades de sus propios emprendimientos
    public boolean isManagedBy(Long providerId) {
        return business.hasProvider(providerId);
    }

    // CU-04 4: no puede haber dos slots activos de la misma actividad a la misma hora. Los cancelados no
    // cuentan. La base lo garantiza igual con la restriccion unica de Slot; esto es para mostrar un buen mensaje.
    public boolean hasActiveSlotAt(LocalDateTime startTime, Slot except) {
        return slots.stream().anyMatch(slot -> slot != except && slot.isActive() && slot.getStartTime().equals(startTime));
    }

    // "2 h 30 min", "45 min", "3 h"
    public String getDurationLabel() {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    // Primera categoria, para mostrar como etiqueta en las tarjetas
    public String getMainCategoryName() {
        return categories.stream().findFirst().map(ActivityCategory::getName).orElse(null);
    }
}
