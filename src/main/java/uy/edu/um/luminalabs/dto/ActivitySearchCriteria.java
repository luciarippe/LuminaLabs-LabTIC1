package uy.edu.um.luminalabs.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import uy.edu.um.luminalabs.entities.Department;

import java.math.BigDecimal;
import java.time.LocalDate;

// Filtros del buscador de actividades (RF-13). Todos son opcionales; se reciben como parametros de la URL.
@Getter
@Setter
public class ActivitySearchCriteria {

    public enum Sort { RECOMMENDED, PRICE, DATE }

    private String q;
    private Department department;
    private Long locationId;
    private Long categoryId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Sort sort = Sort.RECOMMENDED;

    public boolean hasKeyword() {
        return q != null && !q.isBlank();
    }

    public boolean hasSlotFilters() {
        return dateFrom != null || dateTo != null || minPrice != null || maxPrice != null;
    }

    public boolean hasAnyFilter() {
        return hasKeyword() || department != null || locationId != null || categoryId != null || hasSlotFilters();
    }
}
