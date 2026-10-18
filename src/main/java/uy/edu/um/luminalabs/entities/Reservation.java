package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// RESERVATION del MER (RF-16, RF-20). Se crea con el constructor, que toma los lugares del slot y
// congela el precio. El proceso completo de reserva y pago (CU-01) va en un servicio.
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    // RF-18: plazo para pagar una reserva pendiente, contado desde su creacion
    public static final Duration PAYMENT_WINDOW = Duration.ofHours(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tourist_id")
    private Tourist tourist;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    // Lista unidireccional: la clave reservation_id queda en la tabla de participantes
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "reservation_id", nullable = false)
    private List<Participant> participants = new ArrayList<>();

    // Se guarda aparte para listar reservas sin cargar a los participantes (CU-09, CU-13)
    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    // Precio por persona del slot EN EL MOMENTO DE RESERVAR. Es una copia a proposito: si despues el
    // prestador cambia el precio del slot (CU-04 5a), esta reserva no cambia.
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    // RF-17: cantidad de participantes x precio por persona. Es lo que se cobra, y la base para calcular
    // el cargo y la devolucion si se cancela (CU-05 5).
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    // CU-18: la reserva pendiente vence 2 horas despues de esta fecha
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // CU-04 5a y CU-05 4b: el prestador cambio el horario despues de que se hizo la reserva.
    // Desde ese momento el turista puede cancelar con devolucion total, sin importar el plazo.
    @Column(name = "schedule_changed", nullable = false)
    private boolean scheduleChanged;

    // CU-01 pasos 6 y 7: queda pendiente de pago, con los lugares descontados del slot.
    // "now" viene del Clock de la aplicacion (lo pasa el servicio).
    public Reservation(Tourist tourist, Slot slot, List<Participant> participants, LocalDateTime now) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("A reservation needs at least one participant");
        }
        slot.reserveSeats(participants.size());
        this.tourist = tourist;
        this.slot = slot;
        this.participants.addAll(participants);
        this.participantCount = participants.size();
        this.unitPrice = slot.getPrice();
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(participantCount));
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.createdAt = now;
    }

    // Codigo que ven el turista y el prestador (CU-09, CU-13). Sale del id: no hace falta guardarlo.
    public String getCode() {
        return id == null ? null : "R-%06d".formatted(id);
    }

    public boolean isPendingPayment() {
        return status == ReservationStatus.PENDING_PAYMENT;
    }

    // CU-13 6a: hasta cuando se puede pagar una reserva pendiente
    public LocalDateTime getPaymentDeadline() {
        return createdAt.plus(PAYMENT_WINDOW);
    }

    public boolean isPaymentWindowOpen(LocalDateTime now) {
        return now.isBefore(getPaymentDeadline());
    }

    // CU-01 paso 10: el pago fue aprobado
    public void confirm() {
        requirePendingPayment();
        status = ReservationStatus.CONFIRMED;
    }

    // CU-16 4b y CU-18 paso 2: vence sin pago y devuelve sus lugares al slot
    public void expire() {
        requirePendingPayment();
        status = ReservationStatus.EXPIRED;
        slot.releaseSeats(participantCount);
    }

    // RF-20: pendiente o confirmada (todavia ocupa lugares en el slot)
    public boolean isActive() {
        return status == ReservationStatus.PENDING_PAYMENT || status == ReservationStatus.CONFIRMED;
    }

    // CU-05 paso 6: cancelada, con sus lugares de vuelta en el slot. La cancelacion y la devolucion se
    // registran aparte (ver Cancellation y CancellationService).
    public void cancel() {
        if (!isActive()) {
            throw new IllegalStateException("La reserva no se puede cancelar porque está " + status.getLabel().toLowerCase() + ".");
        }
        status = ReservationStatus.CANCELLED;
        slot.releaseSeats(participantCount);
    }

    public void markScheduleChanged() {
        scheduleChanged = true;
    }

    // RF-20: el slot termina en su hora de inicio mas la duracion de la actividad
    public LocalDateTime getEndTime() {
        return slot.getStartTime().plusMinutes(slot.getActivity().getDurationMinutes());
    }

    // CU-18 paso 3: una reserva confirmada pasa a terminada cuando termina su slot (y se puede valorar, CU-08)
    public void finish() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed reservations can finish");
        }
        status = ReservationStatus.FINISHED;
    }

    private void requirePendingPayment() {
        if (!isPendingPayment()) {
            throw new IllegalStateException("La reserva no está pendiente de pago (está " + status.getLabel().toLowerCase() + ").");
        }
    }

    // CU-13 2: las reservas se separan en proximas y pasadas segun el inicio del slot
    public boolean isUpcoming(LocalDateTime now) {
        return now.isBefore(slot.getStartTime());
    }
}
