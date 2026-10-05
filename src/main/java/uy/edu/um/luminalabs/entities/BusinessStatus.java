package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessStatus {
    PENDING("Pendiente"),
    APPROVED("Aprobado"),
    DENIED("Denegado"),
    SUSPENDED("Suspendido");

    private final String label;
}
