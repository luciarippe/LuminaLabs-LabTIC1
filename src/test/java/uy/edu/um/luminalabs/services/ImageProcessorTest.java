package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import uy.edu.um.luminalabs.entities.StoredImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor(
            Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneId.of("America/Montevideo")));

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test
    void shrinksLargePhotosAndSavesThemAsJpeg() throws IOException {
        MockMultipartFile file = new MockMultipartFile("photos", "playa.png", "image/png", png(3200, 2000));

        StoredImage image = processor.process(file);

        assertEquals("image/jpeg", image.getContentType());
        assertEquals(1600, image.getWidth());
        assertEquals(1000, image.getHeight());
        BufferedImage saved = ImageIO.read(new ByteArrayInputStream(image.getData()));
        assertEquals(1600, saved.getWidth());
    }

    @Test
    void neverEnlargesSmallPhotos() throws IOException {
        StoredImage image = processor.process(new MockMultipartFile("photos", "chica.png", "image/png", png(400, 300)));

        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
    }

    @Test
    void rejectsFilesThatAreNotImages() {
        // Un archivo con extension de imagen pero que no lo es: se decodifica, no se mira la extension
        MockMultipartFile fake = new MockMultipartFile("photos", "foto.jpg", "image/jpeg", "no soy una foto".getBytes());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> processor.process(fake));
        assertEquals("\"foto.jpg\" no es una imagen JPG o PNG.", e.getMessage());
    }

    @Test
    void rejectsFilesOverTheSizeLimit() {
        MockMultipartFile huge = new MockMultipartFile("photos", "enorme.jpg", "image/jpeg",
                new byte[(int) ImageProcessor.MAX_BYTES + 1]);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> processor.process(huge));
        assertTrue(e.getMessage().contains("pesa más de 8 MB"));
    }
}
