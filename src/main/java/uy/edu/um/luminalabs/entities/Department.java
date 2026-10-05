package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Lista fija de departamentos del pais (RF-24)
@Getter
@RequiredArgsConstructor
public enum Department {
    ARTIGAS("Artigas"),
    CANELONES("Canelones"),
    CERRO_LARGO("Cerro Largo"),
    COLONIA("Colonia"),
    DURAZNO("Durazno"),
    FLORES("Flores"),
    FLORIDA("Florida"),
    LAVALLEJA("Lavalleja"),
    MALDONADO("Maldonado"),
    MONTEVIDEO("Montevideo"),
    PAYSANDU("Paysandú"),
    RIO_NEGRO("Río Negro"),
    RIVERA("Rivera"),
    ROCHA("Rocha"),
    SALTO("Salto"),
    SAN_JOSE("San José"),
    SORIANO("Soriano"),
    TACUAREMBO("Tacuarembó"),
    TREINTA_Y_TRES("Treinta y Tres");

    private final String label;
}
