package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.dto.ActivitySearchCriteria;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.entities.SlotStatus;
import uy.edu.um.luminalabs.exceptions.InvalidApiParameterException;
import uy.edu.um.luminalabs.repositories.ActivityRepository;
import uy.edu.um.luminalabs.repositories.BusinessRepository;
import uy.edu.um.luminalabs.repositories.LocationRepository;
import uy.edu.um.luminalabs.repositories.RatingRepository;
import uy.edu.um.luminalabs.repositories.SlotRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// RF-25 y CU-17: la API publica de solo lectura. Solo considera emprendimientos aprobados y sus actividades
// (paso 3). Los parametros invalidos se informan con InvalidApiParameterException (2a); sin resultados se
// devuelve una lista vacia (3a).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicApiService {

    static final int TOP_SIZE = 3;
    static final int MIN_KEYWORD_LENGTH = 2;

    private final BusinessRepository businessRepository;
    private final ActivityRepository activityRepository;
    private final LocationRepository locationRepository;
    private final SlotRepository slotRepository;
    private final RatingRepository ratingRepository;
    private final ActivityCatalogService catalogService;
    private final Clock clock;

    // a. Los emprendimientos aprobados, por nombre
    public List<Business> findApprovedBusinesses() {
        return businessRepository.findByStatusOrderByCreatedAtAsc(BusinessStatus.APPROVED).stream()
                .sorted(Comparator.comparing(Business::getLegalName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // b. Las actividades con horarios activos ese dia en el departamento o en la localidad (uno de los dos),
    // cada una con sus horarios de ese dia, en orden de hora
    public DayActivities findActivitiesOfDay(LocalDate date, String department, Long locationId) {
        if (date == null) {
            throw new InvalidApiParameterException("Falta el parámetro 'date' (fecha en formato AAAA-MM-DD).");
        }
        boolean byDepartment = department != null && !department.isBlank();
        if (byDepartment == (locationId != null)) {
            throw new InvalidApiParameterException(
                    "Indicá el lugar con uno solo de los parámetros: 'department' (ej. ROCHA) o 'locationId'.");
        }
        var from = date.atStartOfDay();
        var to = date.plusDays(1).atStartOfDay();
        Department dept = null;
        Location location = null;
        List<Slot> slots;
        if (byDepartment) {
            dept = parseDepartment(department);
            slots = slotRepository.findForDayInDepartment(dept, from, to, SlotStatus.ACTIVE, BusinessStatus.APPROVED);
        } else {
            location = locationRepository.findById(locationId).orElseThrow(() -> new InvalidApiParameterException(
                    "No existe una localidad con id " + locationId + "."));
            slots = slotRepository.findForDayInLocation(locationId, from, to, SlotStatus.ACTIVE, BusinessStatus.APPROVED);
        }
        // Agrupadas por actividad, en el orden del primer horario de cada una
        Map<Activity, List<Slot>> byActivity = slots.stream()
                .collect(Collectors.groupingBy(Slot::getActivity, LinkedHashMap::new, Collectors.toList()));
        List<ActivityDay> activities = byActivity.entrySet().stream()
                .map(entry -> new ActivityDay(entry.getKey(), entry.getValue()))
                .toList();
        return new DayActivities(date, dept, location, activities);
    }

    // c. Las 3 actividades del departamento con mejor promedio de estrellas en el mes (por defecto el actual),
    // considerando las valoraciones de reservas cuyo horario fue en ese mes. Empates: mas valoraciones.
    public TopActivities findTopOfMonth(String department, Integer month, Integer year) {
        if (department == null || department.isBlank()) {
            throw new InvalidApiParameterException("Falta el parámetro 'department' (ej. ROCHA).");
        }
        Department dept = parseDepartment(department);
        YearMonth period = period(month, year);
        List<RatingRepository.RatingSummary> top = ratingRepository.findTopInDepartment(dept, BusinessStatus.APPROVED,
                period.atDay(1).atStartOfDay(), period.plusMonths(1).atDay(1).atStartOfDay(), PageRequest.of(0, TOP_SIZE));
        Map<Long, Activity> activities = activityRepository.findAllById(
                        top.stream().map(RatingRepository.RatingSummary::getActivityId).toList()).stream()
                .collect(Collectors.toMap(Activity::getId, activity -> activity));
        List<RankedActivity> ranking = top.stream()
                .map(summary -> new RankedActivity(activities.get(summary.getActivityId()), summary.getAverage(),
                        summary.getTotal()))
                .toList();
        return new TopActivities(dept, period, ranking);
    }

    // d. Si el emprendimiento existe y esta aprobado (3b: si no existe o no esta aprobado, no esta verificado).
    // El RUT o la cedula se aceptan con o sin puntos, guiones o espacios.
    public Verification verify(String identifier) {
        String digits = identifier == null ? "" : identifier.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new InvalidApiParameterException("Falta el parámetro 'identifier' (RUT o cédula, solo números).");
        }
        Business business = businessRepository.findByIdentifier(digits).filter(Business::isApproved).orElse(null);
        return new Verification(digits, business);
    }

    // e. Las actividades cuyo nombre, descripcion, categorias o localidad contienen la palabra, las mejor
    // valoradas primero (el mismo orden "Recomendadas" del catalogo)
    public List<ActivityCard> search(String keyword) {
        if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
            throw new InvalidApiParameterException("El parámetro 'q' tiene que tener al menos "
                    + MIN_KEYWORD_LENGTH + " caracteres.");
        }
        ActivitySearchCriteria criteria = new ActivitySearchCriteria();
        criteria.setQ(keyword.trim());
        criteria.setSort(ActivitySearchCriteria.Sort.RECOMMENDED);
        return catalogService.search(criteria);
    }

    // Acepta el nombre del enum o el del departamento, sin importar mayusculas ni tildes: ROCHA, rocha,
    // "Treinta y Tres", TREINTA_Y_TRES, Paysandú...
    static Department parseDepartment(String value) {
        String wanted = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return Arrays.stream(Department.values())
                .filter(department -> department.name().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new InvalidApiParameterException("El departamento '" + value
                        + "' no existe. Valores posibles: " + Arrays.stream(Department.values())
                        .map(Department::name).collect(Collectors.joining(", ")) + "."));
    }

    private YearMonth period(Integer month, Integer year) {
        YearMonth current = YearMonth.now(clock);
        if (month != null && (month < 1 || month > 12)) {
            throw new InvalidApiParameterException("El parámetro 'month' tiene que estar entre 1 y 12.");
        }
        if (year != null && (year < 2000 || year > 2100)) {
            throw new InvalidApiParameterException("El parámetro 'year' no es válido.");
        }
        return YearMonth.of(year == null ? current.getYear() : year, month == null ? current.getMonthValue() : month);
    }

    // Una actividad con sus horarios del dia consultado
    public record ActivityDay(Activity activity, List<Slot> slots) {
    }

    // department o location: el lugar consultado (el otro es null)
    public record DayActivities(LocalDate date, Department department, Location location, List<ActivityDay> activities) {
    }

    public record RankedActivity(Activity activity, double average, long ratings) {
    }

    public record TopActivities(Department department, YearMonth period, List<RankedActivity> activities) {
    }

    // business es null si no existe o no esta aprobado
    public record Verification(String identifier, Business business) {

        public boolean isVerified() {
            return business != null;
        }
    }
}
