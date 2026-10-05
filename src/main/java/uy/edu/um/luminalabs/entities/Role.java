package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// RF-05: cada usuario tiene un unico rol
@Getter
@RequiredArgsConstructor
public enum Role {
    TOURIST("Turista"),
    PROVIDER("Prestador"),
    ADMIN("Administrador");

    private final String label;
}
