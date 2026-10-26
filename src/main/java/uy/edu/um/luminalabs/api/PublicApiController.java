package uy.edu.um.luminalabs.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.luminalabs.api.ApiDtos.*;
import uy.edu.um.luminalabs.services.PublicApiService;

import java.time.LocalDate;
import java.util.List;

// RF-25 y CU-17: API publica y de solo lectura para otras plataformas. No requiere sesion (ver SecurityConfig) y
// responde en JSON. Se puede llamar desde el navegador de otro sitio (CORS, solo GET).
// Documentacion y pagina para probarla: /api/docs (springdoc, RNF-05).
@RestController
@RequestMapping(value = "/api/v1", produces = "application/json")
@CrossOrigin(origins = "*", methods = RequestMethod.GET)
@RequiredArgsConstructor
@ApiResponse(responseCode = "400", description = "Parámetros faltantes o inválidos (CU-17 2a)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class PublicApiController {

    private final PublicApiService apiService;
    private final ApiMapper mapper;

    // a
    @GetMapping("/businesses")
    @Tag(name = "Emprendimientos")
    @Operation(summary = "Listado de emprendimientos",
            description = "Todos los emprendimientos aprobados, por nombre, con las localidades donde operan.")
    public List<BusinessDto> businesses() {
        return apiService.findApprovedBusinesses().stream().map(mapper::business).toList();
    }

    // d
    @GetMapping("/businesses/verification")
    @Tag(name = "Emprendimientos")
    @Operation(summary = "Verificar un emprendimiento",
            description = "Informa si existe un emprendimiento aprobado con ese RUT o cédula. Si no existe o no está "
                    + "aprobado, responde verified=false.")
    public VerificationDto verify(
            @Parameter(description = "RUT o cédula (con o sin puntos y guiones)", example = "219999990011")
            @RequestParam(required = false) String identifier) {
        return mapper.verification(apiService.verify(identifier));
    }

    // b
    @GetMapping("/activities/day")
    @Tag(name = "Actividades")
    @Operation(summary = "Actividades de un día",
            description = "Actividades con horarios activos en esa fecha, en un departamento o en una localidad "
                    + "(uno de los dos), cada una con sus horarios de ese día.")
    public ActivitiesOfDayDto activitiesOfDay(
            @Parameter(description = "Fecha (AAAA-MM-DD)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Departamento: su código o su nombre (ej. ROCHA, Treinta y Tres)", example = "ROCHA")
            @RequestParam(required = false) String department,
            @Parameter(description = "Id de la localidad (en lugar de department)")
            @RequestParam(required = false) Long locationId) {
        return mapper.activitiesOfDay(apiService.findActivitiesOfDay(date, department, locationId));
    }

    // c
    @GetMapping("/activities/top")
    @Tag(name = "Actividades")
    @Operation(summary = "Top 3 del mes por departamento",
            description = "Las 3 actividades del departamento con mejor promedio de estrellas, considerando las "
                    + "valoraciones de reservas cuyo horario fue en ese mes. Los empates se resuelven por cantidad de "
                    + "valoraciones. Si no se indica, se usa el mes actual.")
    public TopActivitiesDto top(
            @Parameter(description = "Departamento: su código o su nombre", example = "ROCHA")
            @RequestParam(required = false) String department,
            @Parameter(description = "Mes (1 a 12); por defecto, el actual", example = "10")
            @RequestParam(required = false) Integer month,
            @Parameter(description = "Año; por defecto, el actual", example = "2026")
            @RequestParam(required = false) Integer year) {
        return mapper.top(apiService.findTopOfMonth(department, month, year));
    }

    // e
    @GetMapping("/activities/search")
    @Tag(name = "Actividades")
    @Operation(summary = "Buscar actividades por palabra clave",
            description = "Actividades cuyo nombre, descripción, categorías o localidad contienen la palabra, las mejor "
                    + "valoradas primero.")
    public List<ActivitySearchResultDto> search(
            @Parameter(description = "Palabra a buscar (al menos 2 caracteres)", example = "kayak")
            @RequestParam(required = false) String q) {
        return apiService.search(q).stream().map(mapper::searchResult).toList();
    }
}
