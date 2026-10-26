package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.services.LocationService;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

// Funciones chicas de presentacion para las plantillas. Uso en Thymeleaf: ${@view.initials(business.legalName)}
@Component("view")
@RequiredArgsConstructor
public class ViewHelper {

    private static final int AVATAR_COLORS = 6;

    private final LocationService locationService;

    // Imagen de una actividad: su foto de portada o, si no tiene fotos, la de su departamento; null si tampoco hay
    // (las plantillas muestran un paisaje dibujado)
    public Long coverImage(Activity activity) {
        Long cover = activity.getCoverImageId();
        if (cover != null || activity.getLocation() == null) {
            return cover;
        }
        return locationService.findDepartmentPhoto(activity.getLocation().getDepartment());
    }

    // Hasta dos iniciales (se saltean las palabras de 1 o 2 letras): "Costa Este" -> "CE", "Chacra 142" -> "C1", "Prex" -> "P"
    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String initials = Arrays.stream(name.trim().split("\\s+"))
                .filter(word -> word.length() > 2 || Character.isDigit(word.charAt(0)))
                .map(word -> word.substring(0, 1))
                .limit(2)
                .collect(Collectors.joining());
        return (initials.isEmpty() ? name.trim().substring(0, 1) : initials).toUpperCase(Locale.ROOT);
    }

    // Color fijo para cada registro (clase CSS avatar--c0 ... avatar--c5)
    public String avatarColor(Long id) {
        return "avatar--c" + (id == null ? 0 : Math.floorMod(id, AVATAR_COLORS));
    }
}
