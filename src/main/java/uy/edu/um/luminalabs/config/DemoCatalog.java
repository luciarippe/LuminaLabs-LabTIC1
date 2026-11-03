package uy.edu.um.luminalabs.config;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import uy.edu.um.luminalabs.entities.Bank;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.entities.BusinessType;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.StoredImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

// DATOS DE DEMOSTRACION: lo que hay en src/main/resources/demo/data.json (prestadores, emprendimientos,
// actividades con sus fotos y valoraciones, turistas y fotos de los departamentos). Lo cargan
// DemoDataInitializer y DemoReservationsInitializer. Las fotos estan en demo/photos (creditos en CREDITOS.md).
public record DemoCatalog(List<ProviderData> providers, List<BusinessData> businesses, List<TouristData> tourists,
                          Map<Department, String> departmentPhotos, List<ActivityData> activities) {

    public static final String RESOURCE = "demo/data.json";
    public static final String PHOTOS = "demo/photos/";

    public static DemoCatalog read(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(input, DemoCatalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    // Foto de demo lista para guardar. Ya estan achicadas y guardadas como JPG (ver CREDITOS.md): solo se lee su tamano.
    public static StoredImage photo(String path, LocalDateTime now) {
        try (InputStream input = new ClassPathResource(PHOTOS + path).getInputStream()) {
            byte[] data = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            return new StoredImage("image/jpeg", data, image.getWidth(), image.getHeight(), now);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read demo photo " + path, e);
        }
    }

    public record ProviderData(String username, String firstName, String lastName, LocalDate birthDate) {
    }

    public record TouristData(String username, String firstName, String lastName, LocalDate birthDate) {
    }

    // key: con la que la nombran las actividades. status null = aprobado
    public record BusinessData(String key, BusinessType type, String name, String identifier, BusinessStatus status,
                               String statusReason, String description, String email, String phone, Bank bank,
                               String account, List<String> providers, List<String> locations, String website,
                               String instagram, String facebook, String tiktok) {
    }

    // Horarios: en los dias de la semana de "weekdays" (1 = lunes ... 7 = domingo) a cada hora de "times", durante
    // "weeks" semanas desde manana (null = DemoDataInitializer.WEEKS_OF_SLOTS)
    public record ActivityData(String business, String name, String location, List<String> categories, int duration,
                               int price, int capacity, Integer minimumAge, Integer weeks, List<Integer> weekdays,
                               List<LocalTime> times, String language, String description, String address,
                               String inclusions, String equipment, String importantDetails, List<String> photos,
                               List<ReviewData> reviews) {
    }

    // Una valoracion de una reserva terminada hace "daysAgo" dias
    public record ReviewData(int daysAgo, int stars, String title, String comment) {
    }
}
