package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// PAYMENT del MER: cada intento de pago de una reserva, con su fecha, monto y estado (RF-18).
// Una reserva puede tener varios intentos (ej. uno rechazado y despues uno aprobado).
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentType type;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    // Identificador del pago en la pasarela; null hasta que la pasarela responde
    @Column(name = "external_id")
    private String externalId;

    // De la tarjeta solo se guardan el titular y los ultimos 4 digitos: el numero completo va directo a la
    // pasarela y no queda en nuestra base.
    @Column(name = "card_holder", nullable = false)
    private String cardHolder;

    @Column(name = "card_last_digits", nullable = false, length = 4)
    private String cardLastDigits;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Cuando se supo el resultado (null mientras esta sin respuesta)
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public Payment(Reservation reservation, PaymentType type, BigDecimal amount, String cardHolder,
                   String cardLastDigits, LocalDateTime now) {
        this.reservation = reservation;
        this.type = type;
        this.amount = amount;
        this.cardHolder = cardHolder;
        this.cardLastDigits = cardLastDigits;
        this.status = type == PaymentType.REFUND ? PaymentStatus.PENDING : PaymentStatus.NO_RESPONSE;
        this.createdAt = now;
    }

    // RF-19: la devolucion de una cancelacion, a la misma tarjeta con la que se cobro
    public static Payment refundOf(Payment charge, BigDecimal amount, LocalDateTime now) {
        return new Payment(charge.getReservation(), PaymentType.REFUND, amount, charge.getCardHolder(),
                charge.getCardLastDigits(), now);
    }

    // Referencia que se le manda a la pasarela. Con ella la pasarela reconoce un pedido repetido
    // (no cobra ni devuelve dos veces lo mismo) y se le puede preguntar despues por el resultado.
    public String getReference() {
        return (type == PaymentType.REFUND ? "REF-" : "PAY-") + id;
    }

    public boolean isAwaitingResponse() {
        return status == PaymentStatus.NO_RESPONSE;
    }

    public boolean isApprovedCharge() {
        return type == PaymentType.CHARGE && status == PaymentStatus.APPROVED;
    }

    public void approve(String externalId, LocalDateTime now) {
        requireAwaitingResponse();
        this.status = PaymentStatus.APPROVED;
        this.externalId = externalId;
        this.resolvedAt = now;
    }

    public void reject(String externalId, LocalDateTime now) {
        requireAwaitingResponse();
        this.status = PaymentStatus.REJECTED;
        this.externalId = externalId;
        this.resolvedAt = now;
    }

    // Un cobro se resuelve desde "sin respuesta"; una devolucion, desde "pendiente"
    private void requireAwaitingResponse() {
        if (status != PaymentStatus.NO_RESPONSE && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment " + id + " already resolved as " + status);
        }
    }
}
