package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.luminalabs.entities.Admin;
import uy.edu.um.luminalabs.entities.Provider;
import uy.edu.um.luminalabs.entities.Tourist;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.events.ModerationEvent;
import uy.edu.um.luminalabs.repositories.RatingRepository;
import uy.edu.um.luminalabs.repositories.ReservationRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;
import uy.edu.um.luminalabs.security.SecurityUser;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Gestion de usuarios: listar, bloquear y desbloquear cuentas
@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserAdministrationService service;
    private Admin admin;
    private Tourist tourist;
    private Provider provider;

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(userRepository, reservationRepository, ratingRepository, eventPublisher,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        admin = withId(Admin.builder().firstName("Ada").lastName("Admin").username("admin").email("admin@uy").build(), 1L);
        tourist = withId(Tourist.builder().firstName("José").lastName("Pérez").username("jose").email("jose@uy").build(), 2L);
        provider = withId(Provider.builder().firstName("Ana").lastName("Sosa").username("ana").email("ana@uy").build(), 3L);
    }

    private static <T extends User> T withId(T user, Long id) {
        user.setId(id);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        return user;
    }

    @Test
    void searchFiltersByRoleAndIgnoresAccents() {
        when(userRepository.findAllByOrderByIdDesc()).thenReturn(List.of(provider, tourist, admin));

        UserAdministrationService.UserSearch tourists = service.search(UserAdministrationService.Filter.TOURISTS, "");
        UserAdministrationService.UserSearch jose = service.search(UserAdministrationService.Filter.ALL, "jose perez");

        assertEquals(List.of(tourist), tourists.users());
        assertEquals(3, tourists.all());
        assertEquals(1, tourists.providers());
        assertEquals(List.of(tourist), jose.users());
    }

    @Test
    void blockingPreventsLoginAndNotifies() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(tourist));

        service.block(1L, 2L, "  Reservas falsas reiteradas ");

        assertTrue(tourist.isBlocked());
        assertEquals("Reservas falsas reiteradas", tourist.getBlockedReason());
        assertEquals(NOW, tourist.getBlockedAt());
        assertTrue(SecurityUser.from(tourist).isBlocked(), "el login lo rechaza");
        verify(eventPublisher).publishEvent(any(ModerationEvent.AccountBlocked.class));
    }

    @Test
    void cannotBlockYourselfNorWithoutReason() {
        assertThrows(IllegalStateException.class, () -> service.block(1L, 1L, "Motivo"));

        when(userRepository.findById(2L)).thenReturn(Optional.of(tourist));
        assertThrows(IllegalArgumentException.class, () -> service.block(1L, 2L, " "));
        assertFalse(tourist.isBlocked());
    }

    // Siempre tiene que quedar al menos un administrador que pueda entrar
    @Test
    void cannotBlockTheLastActiveAdmin() {
        Admin other = withId(Admin.builder().firstName("Otro").lastName("Admin").username("otro").email("otro@uy").build(), 5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(other));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.block(1L, 5L, "Motivo"));

        when(userRepository.countActiveAdmins()).thenReturn(2L);
        service.block(1L, 5L, "Motivo");
        assertTrue(other.isBlocked());
    }

    @Test
    void unblockingRestoresAccess() {
        tourist.block("Motivo", NOW);
        when(userRepository.findById(2L)).thenReturn(Optional.of(tourist));

        service.unblock(2L);

        assertFalse(tourist.isBlocked());
        assertNull(tourist.getBlockedReason());
        verify(eventPublisher).publishEvent(any(ModerationEvent.AccountUnblocked.class));
        assertThrows(IllegalStateException.class, () -> service.unblock(2L), "no estaba bloqueada");
    }
}
