package uy.edu.um.luminalabs.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

// Redes sociales de un emprendimiento. El prestador puede escribir su usuario ("@costaeste") o pegar el link;
// toUrl lo convierte en la direccion completa y controla que sea de esa red. Solo se aceptan http/https,
// asi un link guardado nunca puede ejecutar codigo (ej. "javascript:") al tocarlo.
@Getter
@RequiredArgsConstructor
public enum SocialNetwork {
    WEBSITE("Sitio web", null, null),
    INSTAGRAM("Instagram", "instagram.com", "https://www.instagram.com/"),
    FACEBOOK("Facebook", "facebook.com", "https://www.facebook.com/"),
    TIKTOK("TikTok", "tiktok.com", "https://www.tiktok.com/@");

    public static final int MAX_LENGTH = 255;
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9._-]{1,60}");

    private final String label;
    // Dominio de la red (null para el sitio web: cualquiera)
    private final String domain;
    // Direccion del perfil a la que se le agrega el usuario
    private final String profilePrefix;

    // Direccion completa a partir de lo que escribio el prestador; null si lo dejo vacio.
    // Si no es valido lanza IllegalArgumentException con el mensaje para el usuario.
    public String toUrl(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Máximo " + MAX_LENGTH + " caracteres.");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("No puede tener espacios.");
        }
        // En las redes, "costa.este" es un usuario; es un link si tiene "/" o el dominio de la red
        boolean isLink = value.contains("/") || domain != null && value.toLowerCase(Locale.ROOT).contains(domain);
        if (this != WEBSITE && !isLink) {
            String handle = value.startsWith("@") ? value.substring(1) : value;
            if (!HANDLE.matcher(handle).matches()) {
                throw new IllegalArgumentException("Escribí tu usuario de " + label + " (ej.: @tuemprendimiento) o pegá el link de tu perfil.");
            }
            return profilePrefix + handle;
        }
        return validLink(value);
    }

    private String validLink(String value) {
        String withScheme = value.matches("(?i)^https?://.*") ? value : "https://" + value;
        try {
            URI uri = new URI(withScheme);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.contains(".")) {
                throw new IllegalArgumentException(this == WEBSITE
                        ? "Ingresá la dirección de tu sitio (ej.: www.tuemprendimiento.com.uy)."
                        : "Pegá el link de tu perfil de " + label + ".");
            }
            if (domain != null && !host.equals(domain) && !host.endsWith("." + domain)) {
                throw new IllegalArgumentException("Ese link no es de " + label + ".");
            }
            return withScheme;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("El link no es válido.");
        }
    }
}
