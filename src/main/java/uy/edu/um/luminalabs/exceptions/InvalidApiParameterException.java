package uy.edu.um.luminalabs.exceptions;

// CU-17 2a: un parametro de la API publica falta o no es valido. El mensaje describe el problema y se devuelve
// tal cual en la respuesta (400).
public class InvalidApiParameterException extends RuntimeException {

    public InvalidApiParameterException(String message) {
        super(message);
    }
}
