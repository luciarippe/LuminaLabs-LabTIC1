package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Una imagen guardada en la base (foto de una actividad o de un departamento), ya procesada por ImageProcessor.
// No se modifica nunca: una foto nueva es otra fila, asi /photos/{id} se puede guardar en cache para siempre.
@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor
public class StoredImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    // length: en la base de los tests (H2) el tipo binario tiene un tamano maximo; en PostgreSQL es bytea
    @Column(name = "data", nullable = false, length = 20 * 1024 * 1024)
    private byte[] data;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public StoredImage(String contentType, byte[] data, int width, int height, LocalDateTime createdAt) {
        this.contentType = contentType;
        this.data = data;
        this.width = width;
        this.height = height;
        this.createdAt = createdAt;
    }
}
