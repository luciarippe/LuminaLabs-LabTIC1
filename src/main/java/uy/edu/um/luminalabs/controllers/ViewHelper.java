package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.services.LocationService;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

// Funciones chicas de presentacion para las plantillas. Uso en Thymeleaf: ${@view.initials(business.legalName)}
@Component("view")
@RequiredArgsConstructor
public class ViewHelper {

    private static final int AVATAR_COLORS = 6;

    private final LocationService locationService;

    @Value("${app.payments.provider:mock}")
    private String paymentProvider;

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

    // Nombre de la pasarela de pagos que se esta usando, para los textos de cobros y devoluciones
    public String gatewayName() {
        return gatewayName(paymentProvider);
    }

    static String gatewayName(String provider) {
        return "mercadopago".equals(provider) ? "Mercado Pago" : "la pasarela de pagos";
    }

    // Monto con signo, como en un estado de cuenta: "+ $ 2.000", "− $ 1.800"
    public String signedMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return (amount.signum() < 0 ? "− $ " : "+ $ ") + money(amount.abs());
    }

    static String money(BigDecimal amount) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY"))).format(amount);
    }

    // Color fijo para cada registro (clase CSS avatar--c0 ... avatar--c5)
    public String avatarColor(Long id) {
        return "avatar--c" + (id == null ? 0 : Math.floorMod(id, AVATAR_COLORS));
    }
}
