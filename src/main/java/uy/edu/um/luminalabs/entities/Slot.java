package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// SLOT del MER: una fecha y hora concreta en la que se ofrece una actividad (RF-11)
@Entity
@Table(name = "slots", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "start_time"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    // Precio por persona
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    // Empieza igual a la capacidad y se descuenta con cada reserva
    @Column(name = "availability", nullable = false)
    private int availability;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SlotStatus status;

    public Slot(Activity activity, LocalDateTime startTime, BigDecimal price, int maxCapacity) {
        if (price.signum() <= 0 || maxCapacity <= 0) {
            throw new IllegalArgumentException("Price and capacity must be greater than zero");
        }
        this.activity = activity;
        this.startTime = startTime;
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.availability = maxCapacity;
        this.status = SlotStatus.ACTIVE;
    }

    public boolean isSoldOut() {
        return availability == 0;
    }

    // RF-10: se puede reservar hasta "bookingCutoffMinutes" antes del inicio (0 = hasta la hora de inicio)
    public boolean isBookableAt(LocalDateTime now) {
        return status == SlotStatus.ACTIVE
                && availability > 0
                && now.isBefore(startTime.minusMinutes(activity.getBookingCutoffMinutes()));
    }
}
