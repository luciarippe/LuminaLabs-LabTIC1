package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.Test;
import uy.edu.um.luminalabs.dto.ActivitySearchCriteria.Sort;
import uy.edu.um.luminalabs.entities.Activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityCatalogServiceTest {

    private static ActivityCard card(String name, Integer price, LocalDateTime next) {
        Activity activity = new Activity();
        activity.setName(name);
        return new ActivityCard(activity, price == null ? null : BigDecimal.valueOf(price), next);
    }

    private static List<String> sortedNames(Sort sort, ActivityCard... cards) {
        return Stream.of(cards).sorted(ActivityCatalogService.comparatorFor(sort))
                .map(card -> card.activity().getName()).toList();
    }

    private final LocalDateTime tomorrow = LocalDateTime.of(2026, 10, 6, 10, 0);
    private final ActivityCard expensiveSoon = card("Ballenas", 3200, tomorrow);
    private final ActivityCard cheapLater = card("Colonia", 900, tomorrow.plusDays(5));
    private final ActivityCard withoutDates = card("Arequita", null, null);

    @Test
    void sortsByPrice() {
        assertEquals(List.of("Colonia", "Ballenas", "Arequita"),
                sortedNames(Sort.PRICE, withoutDates, expensiveSoon, cheapLater));
    }

    @Test
    void sortsByNextDate() {
        assertEquals(List.of("Ballenas", "Colonia", "Arequita"),
                sortedNames(Sort.DATE, cheapLater, withoutDates, expensiveSoon));
    }

    // Aunque "Arequita" va primero alfabeticamente, sin fechas disponibles queda al final
    @Test
    void recommendedPutsActivitiesWithoutDatesLast() {
        assertEquals(List.of("Ballenas", "Colonia", "Arequita"),
                sortedNames(Sort.RECOMMENDED, withoutDates, cheapLater, expensiveSoon));
    }
}
