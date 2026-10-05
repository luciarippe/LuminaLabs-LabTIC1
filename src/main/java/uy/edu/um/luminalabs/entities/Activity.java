package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Slot addSlot(Slot slot) {
        slots.add(slot);
        return slot;
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
