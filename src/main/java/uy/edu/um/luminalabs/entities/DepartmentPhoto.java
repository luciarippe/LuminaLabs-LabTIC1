package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Foto de un departamento, para "Destinos" en la portada (la carga el administrador). Como Department es una
// lista fija (enum) y no una tabla, la foto va en su propia tabla con el departamento como clave.
@Entity
@Table(name = "department_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentPhoto {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "department", length = 30)
    private Department department;

    // Al reemplazarla o eliminar la fila, la imagen anterior se borra (orphanRemoval), como en Location
    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "photo_id", unique = true)
    private StoredImage photo;

    public DepartmentPhoto(Department department, StoredImage photo) {
        this.department = department;
        this.photo = photo;
    }

    public void replacePhoto(StoredImage photo) {
        this.photo = photo;
    }

    // Id de la foto sin cargar sus bytes
    public Long getPhotoId() {
        return photo.getId();
    }
}
