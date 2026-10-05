package uy.edu.um.luminalabs.exceptions;

import lombok.Getter;

import java.util.Map;

// Errores de reglas de negocio asociados a campos del formulario (ej. correo ya registrado).
// Clave: nombre del campo; valor: mensaje para el usuario.
@Getter
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("Validation failed for fields " + fieldErrors.keySet());
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
}
