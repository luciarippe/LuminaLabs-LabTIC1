package uy.edu.um.luminalabs.security;

import lombok.Getter;
import org.springframework.security.authentication.AccountStatusException;
import uy.edu.um.luminalabs.entities.BusinessStatus;

// CU-14, flujo 4a: prestador con credenciales correctas pero sin emprendimientos aprobados
@Getter
public class ProviderNotApprovedException extends AccountStatusException {

    // null si el prestador no tiene ningun emprendimiento asociado
    private final BusinessStatus status;

    public ProviderNotApprovedException(BusinessStatus status) {
        super("Provider has no approved business");
        this.status = status;
    }
}
