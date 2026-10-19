package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.luminalabs.entities.StoredImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;

// Prepara una foto subida para guardarla: comprueba que sea una imagen de verdad (se decodifica, no se mira la
// extension), la achica a MAX_SIDE pixeles del lado mas largo y la vuelve a guardar como JPG. Volver a
// guardarla descarta los metadatos de la camara, incluida la ubicacion GPS donde se saco la foto.
@Component
@RequiredArgsConstructor
public class ImageProcessor {

    public static final long MAX_BYTES = 8L * 1024 * 1024;
    public static final int MAX_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.85f;

    private final Clock clock;

    // Imagen lista para guardar; si el archivo no sirve lanza IllegalArgumentException con el mensaje para el usuario
    public StoredImage process(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "La foto" : "\"" + file.getOriginalFilename() + "\"";
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(name + " pesa más de " + (MAX_BYTES / 1024 / 1024) + " MB.");
        }
        BufferedImage original;
        try (InputStream input = file.getInputStream()) {
            original = ImageIO.read(input);
        } catch (IOException e) {
            original = null;
        }
        if (original == null) {
            throw new IllegalArgumentException(name + " no es una imagen JPG o PNG.");
        }

        BufferedImage resized = resize(original);
        return new StoredImage("image/jpeg", toJpeg(resized), resized.getWidth(), resized.getHeight(),
                LocalDateTime.now(clock));
    }

    // Achica (nunca agranda) manteniendo la proporcion. Se dibuja sobre fondo blanco: un PNG con
    // transparencias no se ve negro al pasarlo a JPG.
    private static BufferedImage resize(BufferedImage original) {
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
