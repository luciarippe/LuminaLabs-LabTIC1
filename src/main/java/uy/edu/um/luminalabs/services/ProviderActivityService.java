package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.luminalabs.dto.ActivityForm;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.StoredImage;
import uy.edu.um.luminalabs.exceptions.ActivityNotFoundException;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// CU-03: el prestador publica y modifica actividades de sus emprendimientos.
// Reglas de acceso (RNF-10 y RF-06): solo sobre emprendimientos propios, y para publicar o modificar
// el emprendimiento tiene que estar aprobado (uno suspendido no puede publicar).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderActivityService {

    private final ActivityRepository activityRepository;
    private final BusinessRepository businessRepository;
    private final LocationRepository locationRepository;
    private final ActivityCategoryRepository categoryRepository;
    private final Clock clock;
    private final ImageProcessor imageProcessor;

    public List<Activity> findActivities(Long providerId) {
        return activityRepository.findByBusiness_Providers_IdOrderByNameAsc(providerId);
    }

    // Emprendimientos en los que el prestador puede publicar (1a: si hay varios, elige uno)
    public List<Business> findPublishableBusinesses(Long providerId) {
        return businessRepository.findDistinctByProviders_IdOrderByCreatedAtDesc(providerId).stream()
                .filter(Business::isApproved)
                .toList();
    }

    // Si la actividad no existe o es de otro prestador se responde igual (404), para no revelar que existe
    public Activity findOwnActivity(Long providerId, Long activityId) {
        return activityRepository.findWithBusinessById(activityId)
                .filter(activity -> activity.isManagedBy(providerId))
                .orElseThrow(() -> new ActivityNotFoundException(activityId));
    }

    @Transactional
    public Activity create(Long providerId, ActivityForm form) {
        return create(providerId, form, List.of());
    }

    // "uploads": fotos nuevas. Se validan junto con el resto: si alguna no sirve no se guarda nada.
    @Transactional
    public Activity create(Long providerId, ActivityForm form, List<MultipartFile> uploads) {
        Map<String, String> errors = new LinkedHashMap<>();
        Business business = businessRepository.findWithDetailsById(form.getBusinessId())
                .filter(found -> found.hasProvider(providerId) && found.isApproved())
                .orElse(null);
        if (business == null) {
            errors.put("businessId", "Elegí uno de tus emprendimientos aprobados.");
        }
        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setCreatedAt(LocalDateTime.now(clock));
        List<StoredImage> newImages = processPhotos(activity, form, uploads, errors);
        apply(activity, form, errors);
        newImages.forEach(activity::addPhoto);
        return activityRepository.save(activity);
    }

    @Transactional
    public Activity update(Long providerId, Long activityId, ActivityForm form) {
        return update(providerId, activityId, form, List.of());
    }

    // Ademas de los datos: quita las fotos marcadas (form.removePhotoIds), agrega las nuevas y fija la portada
    @Transactional
    public Activity update(Long providerId, Long activityId, ActivityForm form, List<MultipartFile> uploads) {
        Activity activity = findOwnActivity(providerId, activityId);
        requireApprovedBusiness(activity);
        Map<String, String> errors = new LinkedHashMap<>();
        List<StoredImage> newImages = processPhotos(activity, form, uploads, errors);
        apply(activity, form, errors);
        activity.removePhotos(form.getRemovePhotoIds());
        newImages.forEach(activity::addPhoto);
        if (form.getCoverPhotoId() != null) {
            activity.makeCover(form.getCoverPhotoId());
        }
        return activity;
    }

    // Procesa las fotos subidas (las vacias se ignoran) y controla el maximo por actividad. Los problemas van a
    // "errors" con la clave "photos".
    private List<StoredImage> processPhotos(Activity activity, ActivityForm form, List<MultipartFile> uploads,
                                            Map<String, String> errors) {
        List<MultipartFile> files = uploads == null ? List.of()
                : uploads.stream().filter(file -> file != null && !file.isEmpty()).toList();
        long kept = activity.getPhotos().stream()
                .filter(photo -> !form.getRemovePhotoIds().contains(photo.getId()))
                .count();
        if (kept + files.size() > Activity.MAX_PHOTOS) {
            errors.put("photos", "Una actividad puede tener hasta " + Activity.MAX_PHOTOS + " fotos"
                    + (kept > 0 ? " (ya tiene " + kept + ")." : "."));
            return List.of();
        }
        List<StoredImage> images = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                images.add(imageProcessor.process(file));
            } catch (IllegalArgumentException e) {
                errors.put("photos", e.getMessage());
                return List.of();
            }
        }
        return images;
    }

    // Para precargar el formulario de edicion
    public ActivityForm toForm(Activity activity) {
        ActivityForm form = new ActivityForm();
        form.setBusinessId(activity.getBusiness().getId());
        form.setName(activity.getName());
        form.setDescription(activity.getDescription());
        form.setLocationId(activity.getLocation().getId());
        form.setAddress(activity.getAddress());
        form.setDurationMinutes(activity.getDurationMinutes());
        form.setLanguage(activity.getLanguage());
        form.setMinimumAge(activity.getMinimumAge());
        form.setAccessibility(activity.getAccessibility());
        form.setInclusions(activity.getInclusions());
        form.setEquipment(activity.getEquipment());
        form.setImportantDetails(activity.getImportantDetails());
        form.setCategoryIds(activity.getCategories().stream().map(ActivityCategory::getId).toList());
        form.setBookingCutoffMinutes(activity.getBookingCutoffMinutes());
        form.setCancellationDeadlineHours(activity.getCancellationDeadlineHours());
        form.setCancellationFeePercentage(activity.getCancellationFeePercentage());
        return form;
    }

    static void requireApprovedBusiness(Activity activity) {
        if (!activity.getBusiness().isApproved()) {
            throw new IllegalStateException("El emprendimiento " + activity.getBusiness().getLegalName()
                    + " no está aprobado: no se pueden publicar ni modificar sus actividades.");
        }
    }

    // Valida lo que depende de la base de datos (localidad y categorias) y copia el formulario a la entidad
    private void apply(Activity activity, ActivityForm form, Map<String, String> errors) {
        Location location = locationRepository.findById(form.getLocationId()).orElse(null);
        if (location == null) {
            errors.put("locationId", "Elegí una localidad de la lista.");
        }
        List<Long> categoryIds = form.getCategoryIds().stream().distinct().toList();
        List<ActivityCategory> categories = categoryRepository.findAllById(categoryIds);
        if (categories.isEmpty() || categories.size() != categoryIds.size()) {
            errors.put("categoryIds", "Elegí al menos una categoría de la lista.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        activity.setName(form.getName().trim());
        activity.setDescription(form.getDescription().trim());
        activity.setLocation(location);
        activity.setAddress(form.getAddress().trim());
        activity.setDurationMinutes(form.getDurationMinutes());
        activity.setLanguage(form.getLanguage().trim());
        activity.setMinimumAge(form.getMinimumAge());
        activity.setAccessibility(trimToNull(form.getAccessibility()));
        activity.setInclusions(trimToNull(form.getInclusions()));
        activity.setEquipment(trimToNull(form.getEquipment()));
        activity.setImportantDetails(trimToNull(form.getImportantDetails()));
        activity.setCategories(new HashSet<>(categories));
        activity.setBookingCutoffMinutes(form.getBookingCutoffMinutes());
        activity.setCancellationDeadlineHours(form.getCancellationDeadlineHours());
        activity.setCancellationFeePercentage(form.getCancellationFeePercentage());
    }

    // Los campos opcionales vacios se guardan como null, asi el detalle publico no muestra secciones vacias
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
