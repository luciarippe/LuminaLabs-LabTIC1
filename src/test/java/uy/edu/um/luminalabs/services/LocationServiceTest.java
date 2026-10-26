package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import uy.edu.um.luminalabs.dto.LocationForm;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.DepartmentPhoto;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.StoredImage;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.DepartmentPhotoRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// CU-10: mantener las localidades
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;
    @Mock
    private DepartmentPhotoRepository departmentPhotoRepository;
    @Mock
    private ImageProcessor imageProcessor;
    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private LocationService service;

    private static Location location(Long id, String name, Department department) {
        Location location = new Location(name, department);
        location.setId(id);
        return location;
    }

    private static LocationForm form(String name, Department department) {
        LocationForm form = new LocationForm(department);
        form.setName(name);
        return form;
    }

    @Test
    void createsInTheDepartmentWithCleanName() {
        when(locationRepository.findByNameIgnoreCaseAndDepartment("Barra de Valizas", Department.ROCHA)).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location created = service.create(form("  Barra  de Valizas ", Department.ROCHA));

        assertEquals("Barra de Valizas", created.getName());
        assertEquals(Department.ROCHA, created.getDepartment());
    }

    // 6a: el nombre no se repite dentro del mismo departamento (en otro si puede)
    @Test
    void repeatedNameInSameDepartmentIsRejected() {
        when(locationRepository.findByNameIgnoreCaseAndDepartment("la paloma", Department.ROCHA))
                .thenReturn(Optional.of(location(1L, "La Paloma", Department.ROCHA)));

        assertThrows(FieldValidationException.class, () -> service.create(form("la paloma", Department.ROCHA)));
        verify(locationRepository, never()).save(any());
    }

    // 7a: se puede mover a otro departamento; el pronostico vuelve a buscar sus coordenadas
    @Test
    void updateCanMoveToAnotherDepartment() {
        Location florida = location(4L, "Florida", Department.RIVERA);
        when(locationRepository.findById(4L)).thenReturn(Optional.of(florida));
        when(locationRepository.findByNameIgnoreCaseAndDepartment("Florida", Department.FLORIDA)).thenReturn(Optional.empty());

        service.update(4L, form("Florida", Department.FLORIDA));

        assertEquals(Department.FLORIDA, florida.getDepartment());
        verify(weatherService).forgetLocation(4L);
    }

    // 6b: en uso no se elimina, y se dice cuantas actividades y emprendimientos la usan
    @Test
    void locationInUseCannotBeDeleted() {
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L, "La Paloma", Department.ROCHA)));
        when(locationRepository.countActivities(1L)).thenReturn(2L);
        when(locationRepository.countBusinesses(1L)).thenReturn(1L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.delete(1L));
        assertTrue(e.getMessage().contains("2 actividades y 1 emprendimiento"));
        verify(locationRepository, never()).delete(any());
    }

    @Test
    void unusedLocationIsDeleted() {
        Location unused = location(9L, "Aguas Dulces", Department.ROCHA);
        when(locationRepository.findById(9L)).thenReturn(Optional.of(unused));

        service.delete(9L);

        verify(locationRepository).delete(unused);
    }

    // Primer nivel: los 19 departamentos, tambien los que no tienen localidades
    @Test
    void summaryListsEveryDepartment() {
        when(locationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                location(1L, "La Paloma", Department.ROCHA), location(2L, "Rocha", Department.ROCHA)));

        List<LocationService.DepartmentSummary> summaries = service.summarizeDepartments();

        assertEquals(Department.values().length, summaries.size());
        LocationService.DepartmentSummary rocha = summaries.stream()
                .filter(summary -> summary.department() == Department.ROCHA).findFirst().orElseThrow();
        assertEquals(2, rocha.locations());
        assertNull(rocha.photoId());
        assertFalse(rocha.hasPhoto());
    }

    // Las fotos de los departamentos se leen una vez y se vuelven a leer cuando el administrador cambia una
    @Test
    void departmentPhotosAreCachedUntilTheyChange() {
        when(departmentPhotoRepository.findAll()).thenReturn(List.of(new DepartmentPhoto(Department.ROCHA, image(99L))));

        Map<Department, Long> photos = service.findPhotoByDepartment();
        assertEquals(99L, service.findDepartmentPhoto(Department.ROCHA));
        assertNull(service.findDepartmentPhoto(Department.SALTO));
        assertEquals(Map.of(Department.ROCHA, 99L), photos);
        verify(departmentPhotoRepository, times(1)).findAll();

        service.removeDepartmentPhoto(Department.ROCHA);
        when(departmentPhotoRepository.findAll()).thenReturn(List.of());
        assertNull(service.findDepartmentPhoto(Department.ROCHA));
        verify(departmentPhotoRepository, times(2)).findAll();
    }

    @Test
    void uploadingAgainReplacesTheDepartmentPhoto() {
        MockMultipartFile file = new MockMultipartFile("photo", "rocha.jpg", "image/jpeg", new byte[]{1});
        DepartmentPhoto existing = new DepartmentPhoto(Department.ROCHA, image(5L));
        when(imageProcessor.process(file)).thenReturn(image(6L));
        when(departmentPhotoRepository.findById(Department.ROCHA)).thenReturn(Optional.of(existing));

        service.setDepartmentPhoto(Department.ROCHA, file);

        assertEquals(6L, existing.getPhotoId());
        verify(departmentPhotoRepository, never()).save(any());
    }

    @Test
    void departmentPhotoRequiresAFile() {
        assertThrows(IllegalArgumentException.class, () -> service.setDepartmentPhoto(Department.ROCHA, null));
    }

    // Imagen con id, sin bytes reales (solo se usa su id)
    private static StoredImage image(Long id) {
        StoredImage image = new StoredImage("image/jpeg", new byte[0], 10, 10, null);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }
}
