package uy.edu.um.luminalabs.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.services.ImageProcessor;

import java.net.URI;

// Un archivo mas grande que el limite (spring.servlet.multipart.*) se rechaza antes de llegar al controlador:
// se vuelve a la pagina del formulario con un aviso en vez de mostrar una pagina de error
@ControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Cada foto puede pesar hasta "
                + (ImageProcessor.MAX_BYTES / 1024 / 1024) + " MB. Elegí fotos más livianas y volvé a intentarlo.");
        return "redirect:" + sameSitePath(request.getHeader("Referer"));
    }

    // Solo se vuelve a una pagina de este mismo sitio (el Referer lo manda el navegador y se podria falsear)
    private static String sameSitePath(String referer) {
        try {
            URI uri = URI.create(referer == null ? "/" : referer);
            String path = uri.getRawPath();
            return path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//") ? "/" : path;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
