package uy.edu.um.luminalabs.services;

import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BusinessStatusCounts(long pending, long approved, long denied, long suspended) {

    // Cuenta los emprendimientos de una lista ya cargada (ej. los resultados de una busqueda)
    public static BusinessStatusCounts of(List<Business> businesses) {
        Map<BusinessStatus, Long> counts = businesses.stream()
                .collect(Collectors.groupingBy(Business::getStatus, Collectors.counting()));
        return new BusinessStatusCounts(
                counts.getOrDefault(BusinessStatus.PENDING, 0L),
                counts.getOrDefault(BusinessStatus.APPROVED, 0L),
                counts.getOrDefault(BusinessStatus.DENIED, 0L),
                counts.getOrDefault(BusinessStatus.SUSPENDED, 0L));
    }

    public long of(BusinessStatus status) {
        return switch (status) {
            case PENDING -> pending;
            case APPROVED -> approved;
            case DENIED -> denied;
            case SUSPENDED -> suspended;
        };
    }

    public long total() {
        return pending + approved + denied + suspended;
    }
}
