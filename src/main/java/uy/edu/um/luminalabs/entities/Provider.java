package uy.edu.um.luminalabs.entities;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("PROVIDER")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Provider extends User {
    // Más adelante: @ManyToMany private List<Business> businesses;
}