package uy.edu.um.luminalabs.entities;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import uy.edu.um.luminalabs.entities.User;

@Entity
//@DiscriminatorValue es el texto que va en la columna user_type. Cuando Hibernate lee una fila con TOURIST, construye un objeto Tourist.
@DiscriminatorValue("TOURIST")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Tourist extends User {
    // Más adelante: @OneToMany private List<Reservation> reservations;
}