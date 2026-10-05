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

    public Location(String name, Department department) {
        this.name = name;
        this.department = department;
    }
}
