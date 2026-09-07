package uy.edu.um.luminalabs.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name= "usuarios")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Usuario {

    @Id
    private long id;

    @Column (name = "nombre")
    private String name;

    private int edad;

    @Column(name = "apellido")
    private String lastName;

    @Column(name = "nombre usuario")
    private String userName;





}

