package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // Localidades agrupadas por departamento (en el orden del enum), para mostrarlas en los formularios
    public Map<Department, List<Location>> findAllGroupedByDepartment() {
        return locationRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(Location::getDepartment,
                        () -> new EnumMap<>(Department.class),
                        Collectors.toList()));
    }
}
