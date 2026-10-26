package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.Rating;
import uy.edu.um.luminalabs.entities.Reservation;
import uy.edu.um.luminalabs.entities.Role;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.events.MailRecipient;
import uy.edu.um.luminalabs.events.ModerationEvent;
import uy.edu.um.luminalabs.exceptions.UserNotFoundException;
import uy.edu.um.luminalabs.repositories.RatingRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

// Gestion de usuarios desde la administracion: ver todas las cuentas y bloquear o desbloquear una.
// No se borran cuentas (tienen reservas, pagos y valoraciones asociadas): bloquear impide iniciar sesion y se
// puede deshacer.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RatingRepository ratingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Pestanas del listado
    public enum Filter {
        ALL(user -> true),
        TOURISTS(user -> user.getRole() == Role.TOURIST),
        PROVIDERS(user -> user.getRole() == Role.PROVIDER),
        ADMINS(user -> user.getRole() == Role.ADMIN),
        BLOCKED(User::isBlocked);

        private final Predicate<User> predicate;

        Filter(Predicate<User> predicate) {
            this.predicate = predicate;
        }
    }

    // Las cuentas de la pestana que coinciden con la busqueda (nombre, usuario o correo), la mas nueva primero,
    // y cuantas hay en cada pestana para esa busqueda
    public UserSearch search(Filter filter, String query) {
        String wanted = normalize(query);
        List<User> matching = userRepository.findAllByOrderByIdDesc().stream()
                .filter(user -> wanted.isEmpty() || normalize(user.getFullName() + " " + user.getUsername() + " "
                        + user.getEmail()).contains(wanted))
                .toList();
        return new UserSearch(matching.stream().filter(filter.predicate).toList(),
                count(matching, Filter.ALL), count(matching, Filter.TOURISTS), count(matching, Filter.PROVIDERS),
                count(matching, Filter.ADMINS), count(matching, Filter.BLOCKED));
    }

    // La ficha: la cuenta y lo que tiene segun su rol (emprendimientos, reservas, valoraciones)
    public UserDetail detail(Long userId) {
        User user = find(userId);
        List<Business> businesses = user instanceof Provider provider
                ? provider.getBusinesses().stream().sorted(Comparator.comparing(Business::getLegalName)).toList()
                : List.of();
        List<Reservation> reservations = user.getRole() == Role.TOURIST
                ? reservationRepository.findByTourist_IdOrderBySlot_StartTimeAsc(userId).reversed()
                : List.of();
        List<Rating> ratings = user.getRole() == Role.TOURIST
                ? ratingRepository.findByReservation_Tourist_IdOrderByCreatedAtDesc(userId)
                : List.of();
        return new UserDetail(user, businesses, reservations, ratings);
    }

    // No se puede bloquear la propia cuenta ni al ultimo administrador activo (nadie podria administrar el sitio)
    @Transactional
    public User block(Long adminId, Long userId, String reason) {
        if (userId.equals(adminId)) {
            throw new IllegalStateException("No podés bloquear tu propia cuenta.");
        }
        User user = find(userId);
        if (user.getRole() == Role.ADMIN && !user.isBlocked() && userRepository.countActiveAdmins() <= 1) {
            throw new IllegalStateException("No se puede bloquear al único administrador activo.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Escribí el motivo: se lo enviamos por correo a la persona.");
        }
        user.block(reason, LocalDateTime.now(clock));
        eventPublisher.publishEvent(new ModerationEvent.AccountBlocked(MailRecipient.from(user), user.getBlockedReason()));
        return user;
    }

    @Transactional
    public User unblock(Long userId) {
        User user = find(userId);
        user.unblock();
        eventPublisher.publishEvent(new ModerationEvent.AccountUnblocked(MailRecipient.from(user)));
        return user;
    }

    public User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static long count(List<User> users, Filter filter) {
        return users.stream().filter(filter.predicate).count();
    }

    // Sin tildes y en minuscula, para buscar "jose" y encontrar "José"
    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    public record UserSearch(List<User> users, long all, long tourists, long providers, long admins, long blocked) {
    }

    public record UserDetail(User user, List<Business> businesses, List<Reservation> reservations, List<Rating> ratings) {
    }
}
