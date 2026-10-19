package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.SocialLinksForm;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.SocialNetwork;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// Redes sociales de los emprendimientos: se cargan al registrarlo y el prestador las cambia despues
@Service
@RequiredArgsConstructor
public class SocialLinksService {

    private final BusinessRepository businessRepository;

    // Agrega a "errors" los links que no son validos. fieldPrefix es el prefijo de los campos en el formulario
    // (ej. "business.social." en el registro, "" en la pantalla de redes).
    public void validate(SocialLinksForm form, String fieldPrefix, Map<String, String> errors) {
        check(SocialNetwork.WEBSITE, form.getWebsite(), fieldPrefix + "website", errors);
        check(SocialNetwork.INSTAGRAM, form.getInstagram(), fieldPrefix + "instagram", errors);
        check(SocialNetwork.FACEBOOK, form.getFacebook(), fieldPrefix + "facebook", errors);
        check(SocialNetwork.TIKTOK, form.getTiktok(), fieldPrefix + "tiktok", errors);
    }

    // Guarda las direcciones completas en el emprendimiento (el formulario ya tiene que estar validado)
    public void apply(Business business, SocialLinksForm form) {
        business.setWebsiteUrl(SocialNetwork.WEBSITE.toUrl(form.getWebsite()));
        business.setInstagramUrl(SocialNetwork.INSTAGRAM.toUrl(form.getInstagram()));
        business.setFacebookUrl(SocialNetwork.FACEBOOK.toUrl(form.getFacebook()));
        business.setTiktokUrl(SocialNetwork.TIKTOK.toUrl(form.getTiktok()));
    }

    // Un emprendimiento del prestador; si no existe o es de otro, se responde igual (no se revela que existe)
    @Transactional(readOnly = true)
    public Business findOwnBusiness(Long providerId, Long businessId) {
        return businessRepository.findWithDetailsById(businessId)
                .filter(business -> business.hasProvider(providerId))
                .orElseThrow(() -> new BusinessNotFoundException(businessId));
    }

    @Transactional
    public Business update(Long providerId, Long businessId, SocialLinksForm form) {
        Business business = findOwnBusiness(providerId, businessId);
        Map<String, String> errors = new LinkedHashMap<>();
        validate(form, "", errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        apply(business, form);
        return business;
    }

    private static void check(SocialNetwork network, String value, String field, Map<String, String> errors) {
        try {
            network.toUrl(value);
        } catch (IllegalArgumentException e) {
            errors.put(field, e.getMessage());
        }
    }
}
