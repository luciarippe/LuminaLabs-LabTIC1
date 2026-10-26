package uy.edu.um.luminalabs.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uy.edu.um.luminalabs.api.ApiDtos.*;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.ActivityCategory;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.Location;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.services.ActivityCard;
import uy.edu.um.luminalabs.services.PublicApiService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

// Pasa las entidades a los objetos que devuelve la API publica (ApiDtos)
@Component
public class ApiMapper {

    // Direccion del portal, para el enlace de cada actividad
    private final String baseUrl;

    public ApiMapper(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public DepartmentDto department(Department department) {
        return department == null ? null : new DepartmentDto(department.name(), department.getLabel());
    }

    public LocationDto location(Location location) {
        return location == null ? null : new LocationDto(location.getId(), location.getName(), department(location.getDepartment()));
    }

    public BusinessDto business(Business business) {
        List<LocationDto> locations = business.getLocations().stream()
                .sorted(Comparator.comparing(Location::getName))
                .map(this::location)
                .toList();
        SocialLinksDto links = business.hasSocialLinks()
                ? new SocialLinksDto(business.getWebsiteUrl(), business.getInstagramUrl(), business.getFacebookUrl(),
                business.getTiktokUrl())
                : null;
        return new BusinessDto(business.getIdentifier(), business.getLegalName(), business.getType().getLabel(),
                business.getDescription(), locations, links);
    }

    public ActivityDto activity(Activity activity) {
        List<String> categories = activity.getCategories().stream().map(ActivityCategory::getName).sorted().toList();
        return new ActivityDto(activity.getId(), activity.getName(), activity.getDescription(),
                activity.getBusiness().getLegalName(), location(activity.getLocation()), categories,
                activity.getDurationMinutes(), activity.getLanguage(), activity.getMinimumAge(),
                baseUrl + "/activities/" + activity.getId());
    }

    public SlotDto slot(Slot slot) {
        return new SlotDto(slot.getStartTime(), slot.getPrice(), slot.getAvailability(), slot.getMaxCapacity());
    }

    public ActivitySearchResultDto searchResult(ActivityCard card) {
        RatingDto rating = card.hasRatings() ? new RatingDto(round(card.averageRating()), card.ratingCount()) : null;
        return new ActivitySearchResultDto(activity(card.activity()), card.fromPrice(), card.nextStart(), rating);
    }

    public ActivitiesOfDayDto activitiesOfDay(PublicApiService.DayActivities day) {
        List<ActivityOfDayDto> activities = day.activities().stream()
                .map(item -> new ActivityOfDayDto(activity(item.activity()), item.slots().stream().map(this::slot).toList()))
                .toList();
        return new ActivitiesOfDayDto(day.date(), department(day.department()), location(day.location()), activities);
    }

    public TopActivitiesDto top(PublicApiService.TopActivities top) {
        List<RankedActivityDto> ranking = IntStream.range(0, top.activities().size())
                .mapToObj(index -> {
                    PublicApiService.RankedActivity ranked = top.activities().get(index);
                    return new RankedActivityDto(index + 1, activity(ranked.activity()),
                            new RatingDto(round(ranked.average()), ranked.ratings()));
                })
                .toList();
        return new TopActivitiesDto(department(top.department()), top.period().getMonthValue(), top.period().getYear(), ranking);
    }

    public VerificationDto verification(PublicApiService.Verification verification) {
        return new VerificationDto(verification.identifier(), verification.isVerified(),
                verification.isVerified() ? business(verification.business()) : null);
    }

    // Un decimal, como se muestra en el portal (4.5 y no 4.4999999)
    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
