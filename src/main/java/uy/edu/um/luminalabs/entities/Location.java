package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "locations", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "department"}))
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false)
    private Department department;

    // Foto de la localidad (la carga el administrador). LAZY: para mostrarla alcanza con el id.
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "photo_id", unique = true)
    private StoredImage photo;

    // Id de la foto sin cargar sus bytes; null si no tiene
    public Long getPhotoId() {
        return photo == null ? null : photo.getId();
    }

    public Location(String name, Department department) {
        this.name = name;
        this.department = department;
    }
}
