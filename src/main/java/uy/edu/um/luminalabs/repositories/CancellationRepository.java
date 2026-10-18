package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.Cancellation;

import java.util.Optional;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    // CU-13: la cancelacion de una reserva, con su devolucion
    @EntityGraph(attributePaths = "refundPayment")
    Optional<Cancellation> findByReservation_Id(Long reservationId);
}
