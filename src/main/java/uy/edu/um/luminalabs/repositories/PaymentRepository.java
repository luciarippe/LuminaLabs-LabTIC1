package uy.edu.um.luminalabs.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Payment;
import uy.edu.um.luminalabs.entities.PaymentStatus;
import uy.edu.um.luminalabs.entities.PaymentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // CU-13 paso 5: los intentos de pago de una reserva, del primero al ultimo
    List<Payment> findByReservation_IdOrderByCreatedAtAsc(Long reservationId);

    // CU-16 paso 5: intentos de cobro que quedaron sin respuesta
    List<Payment> findByReservation_IdAndTypeAndStatus(Long reservationId, PaymentType type, PaymentStatus status);

    // CU-18 paso 4: devoluciones pendientes creadas antes de cierto momento
    List<Payment> findByTypeAndStatusAndCreatedAtBefore(PaymentType type, PaymentStatus status, LocalDateTime before);

    // CU-18: cobros sin respuesta de reservas que ya no estan pendientes (se cancelaron o vencieron mientras
    // tanto). Si la pasarela los aprobo, hay que devolver la plata.
    @Query("""
            select p.id from Payment p
            where p.type = uy.edu.um.luminalabs.entities.PaymentType.CHARGE
              and p.status = uy.edu.um.luminalabs.entities.PaymentStatus.NO_RESPONSE
              and p.createdAt < :before
              and p.reservation.status <> uy.edu.um.luminalabs.entities.ReservationStatus.PENDING_PAYMENT""")
    List<Long> findOrphanChargeIds(LocalDateTime before);

    // Bloquea el pago mientras se guarda el resultado: la misma devolucion la pueden estar procesando
    // a la vez el pedido del turista y el proceso automatico
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(Long id);
}
