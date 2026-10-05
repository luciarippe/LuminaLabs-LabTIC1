package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    boolean existsByIdentifier(String identifier);

    long countByStatus(BusinessStatus status);

    // Las solicitudes mas antiguas primero, para revisarlas en orden de llegada
    @EntityGraph(attributePaths = "locations")
    List<Business> findByStatusOrderByCreatedAtAsc(BusinessStatus status);

    @EntityGraph(attributePaths = "locations")
    List<Business> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "locations")
    List<Business> findDistinctByProviders_IdOrderByCreatedAtDesc(Long providerId);

    @EntityGraph(attributePaths = {"locations", "providers"})
    Optional<Business> findWithDetailsById(Long id);
}
