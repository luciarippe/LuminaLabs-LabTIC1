package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.Location;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByOrderByNameAsc();
}
