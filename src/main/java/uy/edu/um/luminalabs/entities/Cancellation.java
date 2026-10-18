package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// CANCELLATION del MER (RF-19): cuando y por que se cancelo una reserva, el cargo cobrado y cuanto se devuelve.
// La devolucion en si es un Payment de tipo REFUND, con su propio estado.
@Entity
@Table(name = "cancellations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private CancellationReason reason;

    @Column(name = "cancelled_at", nullable = false)
    private LocalDateTime cancelledAt;

    // Lo que se queda el prestador (CU-05 paso 5)
    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    // Lo que se le devuelve al turista: lo pagado menos el cargo
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    // null si no hay nada que devolver (no habia pagado, o el cargo es el total)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_payment_id")
    private Payment refundPayment;

    public Cancellation(Reservation reservation, CancellationReason reason, BigDecimal feeAmount,
                        BigDecimal refundAmount, Payment refundPayment, LocalDateTime now) {
        this.reservation = reservation;
        this.reason = reason;
        this.feeAmount = feeAmount;
        this.refundAmount = refundAmount;
        this.refundPayment = refundPayment;
        this.cancelledAt = now;
    }
}
