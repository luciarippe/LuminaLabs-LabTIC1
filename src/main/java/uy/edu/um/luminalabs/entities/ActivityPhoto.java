package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una foto de una actividad. "position" ordena la galeria: la 0 es la portada.
// La imagen se carga solo si se piden sus bytes (LAZY); para armar la direccion alcanza con getImageId().
@Entity
@Table(name = "activity_photos")
@Getter
@NoArgsConstructor
public class ActivityPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "image_id", nullable = false, unique = true)
    private StoredImage image;

    @Setter
    @Column(name = "position", nullable = false)
    private int position;

    public ActivityPhoto(Activity activity, StoredImage image, int position) {
        this.activity = activity;
        this.image = image;
        this.position = position;
    }

    // Id de la imagen sin cargar sus bytes (Hibernate lo conoce por la clave foranea)
    public Long getImageId() {
        return image.getId();
    }
}
