package uy.edu.um.luminalabs.events;

import java.time.LocalDateTime;

// CU-15: avisos por correo cuando cambian los prestadores de un emprendimiento. "by" es quien hizo el cambio.
public sealed interface ProviderTeamEvent {

    // Paso 5: se asocio una cuenta de prestador que ya existia
    record Added(MailRecipient recipient, String businessName, String by) implements ProviderTeamEvent {
    }

    // 3a: se lo quito del emprendimiento
    record Removed(MailRecipient recipient, String businessName, String by) implements ProviderTeamEvent {
    }

    // 4a: el correo no tenia cuenta; se le manda el enlace para crearla. El token va solo en este correo.
    record Invited(String email, String businessName, String by, String token, LocalDateTime expiresAt)
            implements ProviderTeamEvent {
    }
}
