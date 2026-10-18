package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// SLOT del MER: una fecha y hora concreta en la que se ofrece una actividad (RF-11).
// Los cambios se hacen solo con los metodos de abajo, que aplican las reglas de CU-04.
// La restriccion unica usa active_start_time (y no start_time) para que un slot cancelado no impida
// crear otro a la misma hora: ver el comentario del campo.
@Entity
@Table(name = "slots", uniqueConstraints = @UniqueConstraint(
        name = "uk_slots_activity_active_start", columnNames = {"activity_id", "active_start_time"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RNF-12: bloqueo optimista. Hibernate suma 1 en cada UPDATE y lo hace con "WHERE version = <la que leyo>".
    // Si otra transaccion modifico el slot en el medio, el UPDATE no encuentra la fila y lanza
    // OptimisticLockException en vez de pisar el cambio. Protege sobre todo las ediciones del prestador
    // (ej. cambia la capacidad mientras entra una reserva). Las reservas entre si usan bloqueo pesimista
    // (SlotRepository.findByIdForUpdate), que hace esperar en vez de fallar.
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    // Igual a startTime mientras el slot esta activo; null si esta cancelado. Sobre esta columna esta la
    // restriccion unica: en SQL dos NULL no se consideran iguales, asi que puede haber muchos slots
    // cancelados a la misma hora pero uno solo activo (CU-04 4). Se actualiza solo dentro de esta clase.
    @Column(name = "active_start_time")
    private LocalDateTime activeStartTime;

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
        requirePositive(price, maxCapacity);
        this.activity = activity;
        this.startTime = startTime;
        this.activeStartTime = startTime;
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.availability = maxCapacity;
        this.status = SlotStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == SlotStatus.ACTIVE;
    }

    public boolean isSoldOut() {
        return availability == 0;
    }

    public boolean hasStarted(LocalDateTime now) {
        return !now.isBefore(startTime);
    }

    // Lugares ya tomados por reservas
    public int getReservedSeats() {
        return maxCapacity - availability;
    }

    // RF-10: se puede reservar hasta "bookingCutoffMinutes" antes del inicio (0 = hasta la hora de inicio)
    public boolean isWithinBookingWindow(LocalDateTime now) {
        return status == SlotStatus.ACTIVE && now.isBefore(startTime.minusMinutes(activity.getBookingCutoffMinutes()));
    }

    public boolean isBookableAt(LocalDateTime now) {
        return isWithinBookingWindow(now) && availability > 0;
    }

    // CU-04 5a: modificar un slot. La capacidad no puede quedar por debajo de los lugares reservados;
    // la disponibilidad se recalcula manteniendo esos lugares. Devuelve true si cambio el horario,
    // para que el servicio avise a los turistas afectados.
    public boolean update(LocalDateTime newStartTime, BigDecimal newPrice, int newMaxCapacity, LocalDateTime now) {
        requireEditable(now);
        requirePositive(newPrice, newMaxCapacity);
        int reserved = getReservedSeats();
        if (newMaxCapacity < reserved) {
            throw new IllegalStateException("La capacidad no puede ser menor que los " + reserved
                    + " lugares ya reservados.");
        }
        boolean scheduleChanged = !newStartTime.equals(startTime);
        startTime = newStartTime;
        activeStartTime = newStartTime;
        price = newPrice;
        maxCapacity = newMaxCapacity;
        availability = newMaxCapacity - reserved;
        return scheduleChanged;
    }

    // CU-04 5b: el slot cancelado deja de mostrarse. Sus reservas las cancela el servicio.
    public void cancel(LocalDateTime now) {
        requireEditable(now);
        status = SlotStatus.CANCELLED;
        activeStartTime = null;
    }

    // CU-01 6: una reserva toma lugares. Nunca deja la disponibilidad negativa; que dos reservas simultaneas
    // no se pisen lo garantiza el bloqueo pesimista con que se lee el slot (SlotRepository.findByIdForUpdate).
    public void reserveSeats(int seats) {
        if (!isActive()) {
            throw new IllegalStateException("El horario está cancelado.");
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("Seats must be greater than zero");
        }
        if (seats > availability) {
            throw new IllegalStateException("No hay lugares suficientes: quedan " + availability + ".");
        }
        availability -= seats;
    }

    // CU-05 9 y CU-18 2: una reserva cancelada o vencida devuelve sus lugares
    public void releaseSeats(int seats) {
        if (seats <= 0 || seats > getReservedSeats()) {
            throw new IllegalArgumentException("Cannot release " + seats + " seats");
        }
        availability += seats;
    }

    private void requireEditable(LocalDateTime now) {
        if (!isActive()) {
            throw new IllegalStateException("El horario está cancelado.");
        }
        if (hasStarted(now)) {
            throw new IllegalStateException("El horario ya comenzó y no se puede modificar.");
        }
    }

    private static void requirePositive(BigDecimal price, int maxCapacity) {
        if (price == null || price.signum() <= 0 || maxCapacity <= 0) {
            throw new IllegalArgumentException("Price and capacity must be greater than zero");
        }
    }
}
