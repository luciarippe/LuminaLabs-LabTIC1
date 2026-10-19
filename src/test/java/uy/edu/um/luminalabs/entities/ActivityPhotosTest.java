package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Galeria de fotos de una actividad: orden, portada, quitar y maximo
class ActivityPhotosTest {

    private Activity activity;

    @BeforeEach
    void setUp() {
        activity = new Activity();
    }

    // Agrega una foto y le pone id (en la base lo pone la secuencia)
    private ActivityPhoto addPhoto(long id) {
        activity.addPhoto(new StoredImage("image/jpeg", new byte[]{1}, 10, 10, LocalDateTime.of(2026, 10, 17, 12, 0)));
        ActivityPhoto photo = activity.getPhotos().getLast();
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private List<Long> order() {
        return activity.getPhotos().stream().map(ActivityPhoto::getId).toList();
    }

    @Test
    void photosKeepTheOrderTheyWereAddedIn() {
        addPhoto(1L);
        addPhoto(2L);
        addPhoto(3L);

        assertEquals(List.of(1L, 2L, 3L), order());
        assertEquals(2, activity.getPhotos().get(2).getPosition());
    }

    @Test
    void chosenPhotoBecomesTheCover() {
        addPhoto(1L);
        addPhoto(2L);
        addPhoto(3L);

        activity.makeCover(3L);

        assertEquals(List.of(3L, 1L, 2L), order());
        assertEquals(0, activity.getPhotos().getFirst().getPosition());
    }

    @Test
    void removingPhotosRenumbersTheRest() {
        addPhoto(1L);
        addPhoto(2L);
        addPhoto(3L);

        activity.removePhotos(List.of(1L, 99L));

        assertEquals(List.of(2L, 3L), order());
        assertEquals(List.of(0, 1), activity.getPhotos().stream().map(ActivityPhoto::getPosition).toList());
    }

    @Test
    void allowsAtMostEightPhotos() {
        for (long id = 1; id <= Activity.MAX_PHOTOS; id++) {
            addPhoto(id);
        }

        assertThrows(IllegalStateException.class, () -> addPhoto(99L));
    }

    @Test
    void usesTheLocationPhotoWhenTheActivityHasNone() {
        Location location = new Location("Cabo Polonio", Department.ROCHA);
        StoredImage locationPhoto = new StoredImage("image/jpeg", new byte[]{1}, 10, 10, LocalDateTime.of(2026, 10, 17, 12, 0));
        ReflectionTestUtils.setField(locationPhoto, "id", 50L);
        location.setPhoto(locationPhoto);
        activity.setLocation(location);

        assertEquals(50L, activity.getCoverImageId());
    }
}
