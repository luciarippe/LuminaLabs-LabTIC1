package uy.edu.um.luminalabs.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uy.edu.um.luminalabs.entities.Business;

// Redes sociales de un emprendimiento, todas opcionales. Se escribe el usuario o el link; el formato lo
// controla SocialLinksService (con SocialNetwork). Va dentro de BusinessForm al registrar y sola al modificar.
@Getter
@Setter
@NoArgsConstructor
public class SocialLinksForm {

    private String website;
    private String instagram;
    private String facebook;
    private String tiktok;

    // Con las redes que ya tiene el emprendimiento
    public SocialLinksForm(Business business) {
        this.website = business.getWebsiteUrl();
        this.instagram = business.getInstagramUrl();
        this.facebook = business.getFacebookUrl();
        this.tiktok = business.getTiktokUrl();
    }
}
