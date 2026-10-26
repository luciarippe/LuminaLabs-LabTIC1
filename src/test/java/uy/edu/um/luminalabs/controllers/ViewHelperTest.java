package uy.edu.um.luminalabs.controllers;

import org.junit.jupiter.api.Test;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.StoredImage;
import uy.edu.um.luminalabs.services.LocationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Imagen de una actividad en las tarjetas: su portada o, si no tiene fotos, la de su departamento
class ViewHelperTest {

    private final LocationService locationService = mock(LocationService.class);
    private final ViewHelper view = new ViewHelper(locationService);

    private static Activity activityIn(Department department) {
        Activity activity = new Activity();
        activity.setLocation(new Location("Localidad", department));
        return activity;
    }

    @Test
    void activityWithoutPhotosUsesTheDepartmentPhoto() {
        when(locationService.findDepartmentPhoto(Department.ROCHA)).thenReturn(99L);

        assertEquals(99L, view.coverImage(activityIn(Department.ROCHA)));
    }

    @Test
    void ownPhotoGoesFirst() {
        Activity activity = activityIn(Department.ROCHA);
        StoredImage image = new StoredImage("image/jpeg", new byte[]{1}, 10, 10, null);
        org.springframework.test.util.ReflectionTestUtils.setField(image, "id", 7L);
        activity.addPhoto(image);

        assertEquals(7L, view.coverImage(activity));
        verifyNoInteractions(locationService);
    }

    // Sin foto propia ni del departamento: null (se muestra el paisaje dibujado)
    @Test
    void noPhotoAtAll() {
        when(locationService.findDepartmentPhoto(Department.SALTO)).thenReturn(null);

        assertNull(view.coverImage(activityIn(Department.SALTO)));
    }

    @Test
    void initialsAndColor() {
        assertEquals("CE", view.initials("Costa Este"));
        assertEquals("C1", view.initials("Chacra 142"));
        assertEquals("avatar--c1", view.avatarColor(7L));
    }
}
