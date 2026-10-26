package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.luminalabs.dto.CategoryForm;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-10: mantener las categorias de actividad
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private ActivityCategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService service;

    private static ActivityCategory category(Long id, String name) {
        ActivityCategory category = new ActivityCategory(name, "Descripción");
        category.setId(id);
        return category;
    }

    private static CategoryForm form(String name, String description) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setDescription(description);
        return form;
    }

    @Test
    void createsWithCleanNameAndOptionalDescription() {
        when(categoryRepository.findByNameIgnoreCase("Observación de aves")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(ActivityCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityCategory created = service.create(form("  Observación   de aves ", "  "));

        assertEquals("Observación de aves", created.getName());
        assertNull(created.getDescription());
    }

    // 6a: el nombre no se repite, sin importar mayusculas
    @Test
    void repeatedNameIsRejected() {
        when(categoryRepository.findByNameIgnoreCase("trekking")).thenReturn(Optional.of(category(2L, "Trekking")));

        FieldValidationException e = assertThrows(FieldValidationException.class, () -> service.create(form("trekking", null)));
        assertTrue(e.getFieldErrors().get("name").contains("Trekking"));
        verify(categoryRepository, never()).save(any());
    }

    // Al modificar, la categoria puede conservar su propio nombre (cambiando, por ejemplo, la descripcion)
    @Test
    void updateCanKeepItsOwnName() {
        ActivityCategory trekking = category(2L, "Trekking");
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(trekking));
        when(categoryRepository.findByNameIgnoreCase("Trekking")).thenReturn(Optional.of(trekking));

        service.update(2L, form("Trekking", "Caminatas largas."));

        assertEquals("Caminatas largas.", trekking.getDescription());
    }

    @Test
    void updateCannotTakeAnotherCategorysName() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, "Trekking")));
        when(categoryRepository.findByNameIgnoreCase("Senderismo")).thenReturn(Optional.of(category(3L, "Senderismo")));

        assertThrows(FieldValidationException.class, () -> service.update(2L, form("Senderismo", null)));
    }

    // 6b: en uso no se elimina, y se dice cuantas actividades la usan
    @Test
    void categoryInUseCannotBeDeleted() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, "Trekking")));
        when(categoryRepository.countActivities(2L)).thenReturn(3L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.delete(2L));
        assertTrue(e.getMessage().contains("3 actividades"));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void unusedCategoryIsDeleted() {
        ActivityCategory unused = category(5L, "Sin uso");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(unused));
        when(categoryRepository.countActivities(5L)).thenReturn(0L);

        service.delete(5L);

        verify(categoryRepository).delete(unused);
    }

    @Test
    void listShowsHowManyActivitiesUseEachCategory() {
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(category(1L, "Aventura"), category(2L, "Trekking")));
        ActivityCategoryRepository.CategoryUsage usage = mock(ActivityCategoryRepository.CategoryUsage.class);
        when(usage.getCategoryId()).thenReturn(2L);
        when(usage.getActivities()).thenReturn(4L);
        when(categoryRepository.countActivitiesByCategory()).thenReturn(List.of(usage));

        List<CategoryService.CategoryRow> rows = service.findAllWithUsage();

        assertFalse(rows.get(0).isInUse());
        assertEquals(4, rows.get(1).activities());
    }
}
