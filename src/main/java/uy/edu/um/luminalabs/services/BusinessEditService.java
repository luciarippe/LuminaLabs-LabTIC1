package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.BusinessEditForm;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// Un prestador corrige los datos de su emprendimiento: nombre, descripcion, localidades, contacto y datos bancarios.
// El tipo y el RUT o cedula no cambian (identifican al emprendimiento y los verifico el administrador). Los cambios
// valen enseguida, sin volver a revision. Un emprendimiento denegado no se edita aca: se vuelve a postular con el
// formulario de registro y el mismo RUT o cedula (CU-02 5c), y asi pasa de nuevo por revision.
@Service
@RequiredArgsConstructor
public class BusinessEditService {

    private final SocialLinksService socialLinksService;
    private final LocationRepository locationRepository;
    private final ActivityRepository activityRepository;

    // Un emprendimiento del prestador que se puede editar
    @Transactional(readOnly = true)
    public Business findEditableBusiness(Long providerId, Long businessId) {
        Business business = socialLinksService.findOwnBusiness(providerId, businessId);
        if (business.getStatus() == BusinessStatus.DENIED) {
            throw new IllegalStateException("La solicitud de " + business.getLegalName() + " fue denegada: para "
                    + "corregir sus datos, volvé a postularla desde \"Registrar otro\" con el mismo RUT o cédula.");
        }
        return business;
    }

    @Transactional
    public Business update(Long providerId, Long businessId, BusinessEditForm form) {
        Business business = findEditableBusiness(providerId, businessId);
        Map<String, String> errors = new LinkedHashMap<>();
        String accountError = form.getBank().accountNumberError(form.getBankAccountNumber());
        if (accountError != null) {
            errors.put("bankAccountNumber", accountError);
        }
        List<Long> locationIds = form.getLocationIds().stream().distinct().toList();
        List<Location> locations = locationRepository.findAllById(locationIds);
        if (locations.isEmpty() || locations.size() != locationIds.size()) {
            errors.put("locationIds", "Elegí al menos una localidad válida.");
        } else {
            String inUse = locationsStillInUse(businessId, new HashSet<>(locationIds));
            if (inUse != null) {
                errors.put("locationIds", inUse);
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        business.setLegalName(form.getLegalName().trim());
        business.setDescription(form.getDescription().trim());
        business.setContactEmail(form.getContactEmail().trim().toLowerCase(Locale.ROOT));
        business.setContactPhone(form.getContactPhone().trim());
        business.setBank(form.getBank());
        business.setBankAccountNumber(form.getBankAccountNumber().trim());
        business.getLocations().clear();
        business.getLocations().addAll(locations);
        return business;
    }

    // Una localidad que usan actividades del emprendimiento no se puede quitar: las actividades tienen que estar en
    // una localidad del emprendimiento. Devuelve el mensaje de error, o null si no se quita ninguna en uso.
    private String locationsStillInUse(Long businessId, Set<Long> keptIds) {
        Set<String> removed = new TreeSet<>();
        for (Activity activity : activityRepository.findByBusiness_Id(businessId)) {
            if (!keptIds.contains(activity.getLocation().getId())) {
                removed.add(activity.getLocation().getName());
            }
        }
        if (removed.isEmpty()) {
            return null;
        }
        return "No podés quitar " + String.join(", ", removed) + ": " + (removed.size() == 1 ? "la usan" : "las usan")
                + " actividades del emprendimiento. Primero cambiales la localidad a esas actividades.";
    }
}
