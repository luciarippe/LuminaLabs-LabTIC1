package uy.edu.um.luminalabs.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uy.edu.um.luminalabs.api.ApiDtos.ApiError;
import uy.edu.um.luminalabs.exceptions.InvalidApiParameterException;

// CU-17 2a: en la API publica los errores se responden en JSON (y no con la pagina de error del portal),
// con un mensaje que describe el problema
@RestControllerAdvice(assignableTypes = PublicApiController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidApiParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidParameter(InvalidApiParameterException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    // Un parametro con un formato que no se puede convertir (ej. date=mañana, month=diez)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError wrongType(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String hint = switch (e.getName()) {
            case "date" -> " Usá el formato AAAA-MM-DD (ej. 2026-11-01).";
            case "month", "year", "locationId" -> " Tiene que ser un número.";
            default -> "";
        };
        return badRequest("El parámetro '" + e.getName() + "' no es válido: '" + e.getValue() + "'." + hint, request);
    }

    private static ApiError badRequest(String message, HttpServletRequest request) {
        return new ApiError(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message,
                request.getRequestURI());
    }
}
