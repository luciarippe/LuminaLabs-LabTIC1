package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    @EntityGraph(attributePaths = "providers")
    Optional<Business> findByIdentifier(String identifier);

    long countByStatus(BusinessStatus status);

    // Las solicitudes mas antiguas primero, para revisarlas en orden de llegada
    @EntityGraph(attributePaths = "locations")
    List<Business> findByStatusOrderByCreatedAtAsc(BusinessStatus status);

    @EntityGraph(attributePaths = "locations")
    List<Business> findAllByOrderByCreatedAtDesc();

    // Rechazados o suspendidos: el cambio de estado mas reciente primero, con sus prestadores (se muestran en el listado)
    @EntityGraph(attributePaths = "providers")
    List<Business> findByStatusOrderByReviewedAtDesc(BusinessStatus status);

    // Busqueda del administrador: parte del nombre (sin distinguir mayusculas) o parte del RUT/cedula.
    // "digits" vacio = no se busca por identificador. Los dos textos ya vienen con %, _ y \ escapados.
    @EntityGraph(attributePaths = "locations")
    @Query("""
            select b from Business b
            where lower(b.legalName) like lower(concat('%', :text, '%')) escape '\\'
               or (:digits <> '' and b.identifier like concat('%', :digits, '%'))
            order by b.createdAt desc""")
    List<Business> search(@Param("text") String text, @Param("digits") String digits);

    // Mis emprendimientos: con sus prestadores (se muestra cuantos tiene cada uno, CU-15)
    @EntityGraph(attributePaths = {"locations", "providers"})
    List<Business> findDistinctByProviders_IdOrderByCreatedAtDesc(Long providerId);

    @EntityGraph(attributePaths = {"locations", "providers"})
    Optional<Business> findWithDetailsById(Long id);
}
