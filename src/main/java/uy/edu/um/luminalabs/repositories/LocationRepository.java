package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByOrderByNameAsc();

    List<Location> findByDepartmentOrderByNameAsc(Department department);

    // CU-10 paso 6: el nombre no se repite dentro del mismo departamento (sin distinguir mayusculas)
    Optional<Location> findByNameIgnoreCaseAndDepartment(String name, Department department);

    // CU-10 6b: cuantas actividades y emprendimientos usan cada localidad (las que no se usan no aparecen)
    @Query("select a.location.id as locationId, count(a) as total from Activity a group by a.location.id")
    List<LocationUsage> countActivitiesByLocation();

    @Query("select l.id as locationId, count(b) as total from Business b join b.locations l group by l.id")
    List<LocationUsage> countBusinessesByLocation();

    @Query("select count(a) from Activity a where a.location.id = :locationId")
    long countActivities(Long locationId);

    @Query("select count(b) from Business b join b.locations l where l.id = :locationId")
    long countBusinesses(Long locationId);

    interface LocationUsage {
        Long getLocationId();

        long getTotal();
    }
}
