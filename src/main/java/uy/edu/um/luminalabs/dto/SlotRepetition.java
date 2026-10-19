package uy.edu.um.luminalabs.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Como se repite un horario al agregarlo (CU-04): el prestador carga uno y se crean todos hasta "repeatUntil"
@Getter
@RequiredArgsConstructor
public enum SlotRepetition {
    NONE("No se repite", 0),
    DAILY("Todos los días", 1),
    EVERY_OTHER_DAY("Cada 2 días", 2),
    WEEKLY("Todas las semanas", 7),
    EVERY_OTHER_WEEK("Cada 2 semanas", 14),
    WEEKDAYS("Ciertos días de la semana", 1);

    private final String label;
    // Cada cuantos dias se repite (WEEKDAYS recorre dia por dia y se queda con los dias elegidos)
    private final int stepDays;

    // Fechas desde "first" hasta "until" (inclusive). Para WEEKDAYS, solo las de los dias elegidos.
    public List<LocalDate> dates(LocalDate first, LocalDate until, Collection<DayOfWeek> weekdays) {
        List<LocalDate> dates = new ArrayList<>();
        if (this == NONE) {
            dates.add(first);
            return dates;
        }
        for (LocalDate date = first; !date.isAfter(until); date = date.plusDays(stepDays)) {
            if (this != WEEKDAYS || weekdays.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }
}
