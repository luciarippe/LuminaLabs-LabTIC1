package uy.edu.um.luminalabs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// ACTIVITY_CATEGORY del MER; la mantienen los administradores (RF-08, RF-24)
@Entity
@Table(name = "activity_categories")
@Getter
@Setter
@NoArgsConstructor
public class ActivityCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    public ActivityCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
