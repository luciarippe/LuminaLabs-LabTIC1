package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Department;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    // Busqueda del catalogo (ver ActivitySpecifications), trayendo lo que muestran las tarjetas en la misma consulta
    @Override
    @EntityGraph(attributePaths = {"business", "location", "categories"})
    List<Activity> findAll(Specification<Activity> specification);

    @EntityGraph(attributePaths = {"business", "location", "categories"})
    Optional<Activity> findWithDetailsByIdAndBusinessStatus(Long id, BusinessStatus status);

    // Espacio del prestador (CU-03): sus actividades y una actividad con lo necesario para verificar que es suya
    @EntityGraph(attributePaths = {"business", "location", "categories"})
    List<Activity> findByBusiness_Providers_IdOrderByNameAsc(Long providerId);

    @EntityGraph(attributePaths = {"business", "business.providers", "location", "categories"})
    Optional<Activity> findWithBusinessById(Long id);

    @Query("""
            select a.location.department as department, count(a) as total
            from Activity a
            where a.business.status = :status
            group by a.location.department
            order by count(a) desc""")
    List<DepartmentCount> countByDepartment(BusinessStatus status);

    interface DepartmentCount {
        Department getDepartment();

        long getTotal();
    }
}
