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

    private ActivityCard rated(String name, Double average, long count) {
        Activity activity = new Activity();
        activity.setName(name);
        return new ActivityCard(activity, BigDecimal.valueOf(1000), tomorrow, average, count);
    }

    // Mejor valoradas primero, pero una sola resena de 5 no le gana a muchas buenas; sin valoraciones queda como
    // una actividad "neutra" (por encima de las mal valoradas)
    @Test
    void recommendedSortsByWeightedRating() {
        ActivityCard manyGood = rated("Kayak", 4.5, 8);
        ActivityCard oneFive = rated("Bodega", 5.0, 1);
        ActivityCard none = rated("Arequita", null, 0);
        ActivityCard bad = rated("Ballenas", 2.0, 4);

        assertEquals(List.of("Kayak", "Bodega", "Arequita", "Ballenas"),
                sortedNames(Sort.RECOMMENDED, none, bad, oneFive, manyGood));
    }

    @Test
    void recommendationScoreWeighsTheNumberOfRatings() {
        assertEquals(3.0, rated("Sin", null, 0).recommendationScore(), 0.001);
        assertEquals(11.0 / 3, rated("Una", 5.0, 1).recommendationScore(), 0.001);
        assertEquals(4.2, rated("Muchas", 4.5, 8).recommendationScore(), 0.001);
    }

    // A igual puntaje va primero la que tiene mas valoraciones
    @Test
    void tiesGoToTheMostRated() {
        assertEquals(List.of("Muchas", "Pocas"),
                sortedNames(Sort.RECOMMENDED, rated("Pocas", 3.0, 1), rated("Muchas", 3.0, 6)));
    }
}
