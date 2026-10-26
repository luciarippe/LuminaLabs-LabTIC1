package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uy.edu.um.luminalabs.entities.ActivityCategory;

import java.util.List;
import java.util.Optional;

public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory, Long> {

    List<ActivityCategory> findAllByOrderByNameAsc();

    // CU-10 paso 6: el nombre no se repite (sin distinguir mayusculas)
    Optional<ActivityCategory> findByNameIgnoreCase(String name);

    // CU-10 6b: cuantas actividades usan cada categoria (las que no usa ninguna no aparecen)
    @Query("select c.id as categoryId, count(a) as activities from Activity a join a.categories c group by c.id")
    List<CategoryUsage> countActivitiesByCategory();

    @Query("select count(a) from Activity a join a.categories c where c.id = :categoryId")
    long countActivities(Long categoryId);

    interface CategoryUsage {
        Long getCategoryId();

        long getActivities();
    }
}
