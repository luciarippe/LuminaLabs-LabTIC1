package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.repositories.LocationRepository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final ImageProcessor imageProcessor;

    // Localidades agrupadas por departamento (en el orden del enum), para mostrarlas en los formularios
    public Map<Department, List<Location>> findAllGroupedByDepartment() {
        return locationRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(Location::getDepartment,
                        () -> new EnumMap<>(Department.class),
                        Collectors.toList()));
    }

    // Una foto por departamento (la de alguna de sus localidades), para las tarjetas de destinos de la portada.
    // Los departamentos sin ninguna localidad con foto no aparecen.
    public Map<Department, Long> findPhotoByDepartment() {
        Map<Department, Long> photos = new EnumMap<>(Department.class);
        locationRepository.findAllByOrderByNameAsc().stream()
                .filter(location -> location.getPhotoId() != null)
                .forEach(location -> photos.putIfAbsent(location.getDepartment(), location.getPhotoId()));
        return photos;
    }

    // Administrador: sube o reemplaza la foto de una localidad. Si el archivo no sirve lanza
    // IllegalArgumentException con el mensaje para el usuario.
    @Transactional
    public Location setPhoto(Long locationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Elegí una foto.");
        }
        Location location = find(locationId);
        location.setPhoto(imageProcessor.process(file));
        return location;
    }

    @Transactional
    public Location removePhoto(Long locationId) {
        Location location = find(locationId);
        location.setPhoto(null);
        return location;
    }

    private Location find(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("La localidad no existe."));
    }
}
