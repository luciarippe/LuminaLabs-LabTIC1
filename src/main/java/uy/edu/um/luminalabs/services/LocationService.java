package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.luminalabs.dto.LocationForm;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.DepartmentPhoto;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.StoredImage;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.DepartmentPhotoRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Localidades: las listas para los formularios y el portal, y su mantenimiento por el administrador (CU-10, RF-24).
// Las actividades y emprendimientos guardan la localidad (no una copia del nombre), asi que modificarla se
// refleja en todos los que la usan (7a).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final DepartmentPhotoRepository departmentPhotoRepository;
    private final ImageProcessor imageProcessor;
    private final WeatherService weatherService;

    // Localidades agrupadas por departamento (en el orden del enum), para mostrarlas en los formularios
    public Map<Department, List<Location>> findAllGroupedByDepartment() {
        return locationRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(Location::getDepartment,
                        () -> new EnumMap<>(Department.class),
                        Collectors.toList()));
    }

    // ---------- Fotos de los departamentos ----------
    // Se muestran en "Destinos" de la portada y como imagen de las actividades sin fotos propias (en cada tarjeta).
    // Son a lo sumo 19 y cambian muy poco, asi que se guardan en memoria y se vuelven a leer solo cuando el
    // administrador sube o quita una.

    private volatile Map<Department, Long> departmentPhotos;

    // Id de la foto de cada departamento que tiene (los que no tienen no aparecen)
    public Map<Department, Long> findPhotoByDepartment() {
        Map<Department, Long> photos = departmentPhotos;
        if (photos == null) {
            photos = new EnumMap<>(Department.class);
            for (DepartmentPhoto photo : departmentPhotoRepository.findAll()) {
                photos.put(photo.getDepartment(), photo.getPhotoId());
            }
            photos = Collections.unmodifiableMap(photos);
            departmentPhotos = photos;
        }
        return photos;
    }

    // Id de la foto del departamento, o null si no tiene
    public Long findDepartmentPhoto(Department department) {
        return findPhotoByDepartment().get(department);
    }

    // Administrador: sube o reemplaza la foto del departamento (la anterior se borra)
    @Transactional
    public void setDepartmentPhoto(Department department, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Elegí una foto.");
        }
        StoredImage image = imageProcessor.process(file);
        departmentPhotoRepository.findById(department).ifPresentOrElse(
                photo -> photo.replacePhoto(image),
                () -> departmentPhotoRepository.save(new DepartmentPhoto(department, image)));
        departmentPhotos = null;
    }

    // Sin foto, la portada y las actividades sin fotos de ese departamento muestran el paisaje dibujado
    @Transactional
    public void removeDepartmentPhoto(Department department) {
        departmentPhotoRepository.deleteById(department);
        departmentPhotos = null;
    }

    // ---------- Administrador (CU-10) ----------

    // Paso 3, primer nivel: los 19 departamentos (tambien los que no tienen localidades), con sus totales
    public List<DepartmentSummary> summarizeDepartments() {
        Map<Department, List<Location>> byDepartment = findAllGroupedByDepartment();
        Map<Department, Long> photos = findPhotoByDepartment();
        return Arrays.stream(Department.values())
                .map(department -> new DepartmentSummary(department,
                        byDepartment.getOrDefault(department, List.of()).size(), photos.get(department)))
                .toList();
    }

    // Paso 3, dentro de un departamento: sus localidades con cuantas actividades y emprendimientos las usan
    public List<LocationRow> findByDepartment(Department department) {
        Map<Long, Long> activities = toMap(locationRepository.countActivitiesByLocation());
        Map<Long, Long> businesses = toMap(locationRepository.countBusinessesByLocation());
        return locationRepository.findByDepartmentOrderByNameAsc(department).stream()
                .map(location -> new LocationRow(location, activities.getOrDefault(location.getId(), 0L),
                        businesses.getOrDefault(location.getId(), 0L)))
                .toList();
    }

    public Location find(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("La localidad no existe (puede que otro administrador la haya eliminado)."));
    }

    @Transactional
    public Location create(LocationForm form) {
        String name = cleanName(form.getName());
        requireUniqueName(name, form.getDepartment(), null);
        return locationRepository.save(new Location(name, form.getDepartment()));
    }

    // 7a. Se puede cambiar el nombre y el departamento (por ejemplo, si se cargo en el departamento equivocado).
    @Transactional
    public Location update(Long locationId, LocationForm form) {
        Location location = find(locationId);
        String name = cleanName(form.getName());
        requireUniqueName(name, form.getDepartment(), locationId);
        location.setName(name);
        location.setDepartment(form.getDepartment());
        // El pronostico se busca por nombre y departamento: que vuelva a buscar sus coordenadas
        weatherService.forgetLocation(locationId);
        return location;
    }

    // 6b: una localidad en uso no se elimina; se informa cuantas actividades y emprendimientos la usan
    @Transactional
    public Location delete(Long locationId) {
        Location location = find(locationId);
        long activities = locationRepository.countActivities(locationId);
        long businesses = locationRepository.countBusinesses(locationId);
        if (activities > 0 || businesses > 0) {
            throw new IllegalArgumentException("No se puede eliminar " + location.getName() + ": la usan "
                    + usage(activities, businesses) + ".");
        }
        locationRepository.delete(location);
        return location;
    }

    // 6a. "currentId" es la localidad que se esta modificando (puede conservar su nombre)
    private void requireUniqueName(String name, Department department, Long currentId) {
        locationRepository.findByNameIgnoreCaseAndDepartment(name, department)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new FieldValidationException(Map.of("name",
                            "Ya existe " + existing.getName() + " en " + department.getLabel() + "."));
                });
    }

    // Sin espacios de mas: "  Punta   del Este " -> "Punta del Este"
    private static String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static Map<Long, Long> toMap(List<LocationRepository.LocationUsage> usage) {
        return usage.stream().collect(Collectors.toMap(LocationRepository.LocationUsage::getLocationId,
                LocationRepository.LocationUsage::getTotal));
    }

    // "3 actividades y 1 emprendimiento"
    static String usage(long activities, long businesses) {
        String activitiesText = activities == 1 ? "1 actividad" : activities + " actividades";
        String businessesText = businesses == 1 ? "1 emprendimiento" : businesses + " emprendimientos";
        if (activities > 0 && businesses > 0) {
            return activitiesText + " y " + businessesText;
        }
        return activities > 0 ? activitiesText : businessesText;
    }

    // Un departamento en la lista: cuantas localidades tiene y su foto (null si no tiene)
    public record DepartmentSummary(Department department, int locations, Long photoId) {

        public boolean hasPhoto() {
            return photoId != null;
        }
    }

    // Una localidad del departamento, con cuantas actividades y emprendimientos la usan
    public record LocationRow(Location location, long activities, long businesses) {

        public boolean isInUse() {
            return activities > 0 || businesses > 0;
        }

        public String getUsage() {
            return isInUse() ? "La usan " + LocationService.usage(activities, businesses) : "Sin uso";
        }
    }
}
