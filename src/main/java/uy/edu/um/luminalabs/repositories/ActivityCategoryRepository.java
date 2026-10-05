package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.ActivityCategory;

import java.util.List;

public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory, Long> {

    List<ActivityCategory> findAllByOrderByNameAsc();
}
