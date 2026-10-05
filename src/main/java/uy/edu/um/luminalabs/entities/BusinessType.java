package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessType {
    COMPANY("Empresa", "RUT"),
    INDIVIDUAL("Particular", "Cédula");

    private final String label;
    private final String identifierLabel;
}
