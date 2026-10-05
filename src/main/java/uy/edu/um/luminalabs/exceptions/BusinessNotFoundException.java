package uy.edu.um.luminalabs.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException(Long id) {
        super("Business not found: " + id);
    }
}
