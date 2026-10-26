package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.CategoryForm;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// CU-10 y RF-24: el administrador mantiene el catalogo de categorias de actividad.
// Las actividades guardan la categoria (no una copia del nombre), asi que modificar una categoria se refleja
// en todas las que la usan (7a).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final ActivityCategoryRepository categoryRepository;

    // Paso 3: las categorias en orden alfabetico, con cuantas actividades usa cada una
    public List<CategoryRow> findAllWithUsage() {
        Map<Long, Long> usage = categoryRepository.countActivitiesByCategory().stream()
                .collect(Collectors.toMap(ActivityCategoryRepository.CategoryUsage::getCategoryId,
                        ActivityCategoryRepository.CategoryUsage::getActivities));
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryRow(category, usage.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    public ActivityCategory find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("La categoría no existe (puede que otro administrador la haya eliminado)."));
    }

    @Transactional
    public ActivityCategory create(CategoryForm form) {
        String name = cleanName(form.getName());
        requireUniqueName(name, null);
        return categoryRepository.save(new ActivityCategory(name, cleanDescription(form.getDescription())));
    }

    // 7a
    @Transactional
    public ActivityCategory update(Long id, CategoryForm form) {
        ActivityCategory category = find(id);
        String name = cleanName(form.getName());
        requireUniqueName(name, id);
        category.setName(name);
        category.setDescription(cleanDescription(form.getDescription()));
        return category;
    }

    // 6b: una categoria en uso no se elimina
    @Transactional
    public ActivityCategory delete(Long id) {
        ActivityCategory category = find(id);
        long activities = categoryRepository.countActivities(id);
        if (activities > 0) {
            throw new IllegalStateException("No se puede eliminar \"" + category.getName() + "\": la "
                    + (activities == 1 ? "usa 1 actividad" : "usan " + activities + " actividades")
                    + ". Primero hay que sacarla de esas actividades.");
        }
        categoryRepository.delete(category);
        return category;
    }

    // 6a. "currentId" es la categoria que se esta modificando (puede conservar su nombre)
    private void requireUniqueName(String name, Long currentId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new FieldValidationException(Map.of("name",
                            "Ya existe la categoría \"" + existing.getName() + "\"."));
                });
    }

    // Sin espacios de mas: "  Turismo   rural " -> "Turismo rural"
    private static String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static String cleanDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    // Una fila del listado: la categoria y cuantas actividades la usan
    public record CategoryRow(ActivityCategory category, long activities) {

        public boolean isInUse() {
            return activities > 0;
        }
    }
}
