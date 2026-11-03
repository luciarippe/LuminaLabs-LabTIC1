package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.PasswordResetForm;
import uy.edu.um.luminalabs.entities.PasswordResetToken;
import uy.edu.um.luminalabs.entities.Tourist;
import uy.edu.um.luminalabs.events.PasswordResetRequestedEvent;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.PasswordResetTokenRepository;
import uy.edu.um.luminalabs.repositories.UserRepository;
import uy.edu.um.luminalabs.security.UserSessionTerminator;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Recuperar la contrasena olvidada
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserSessionTerminator sessionTerminator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PasswordResetService service;
    private Tourist ana;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, sessionTerminator,
                eventPublisher, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        ana = Tourist.builder().firstName("Ana").lastName("Pérez").username("anaperez").email("ana@correo.uy")
                .passwordHash("hash-viejo").birthDate(LocalDate.of(1995, 3, 1)).build();
        ana.setId(7L);
    }

    @Test
    void sendsALinkThatExpiresInAnHourAndKeepsOnlyTheHashOfTheToken() {
        when(userRepository.findByEmailIgnoreCase("ana@correo.uy")).thenReturn(Optional.of(ana));
        when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestReset("  Ana@Correo.uy ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        String token = event.getValue().token();
        assertEquals(SecureTokens.hash(token), saved.getValue().getTokenHash());
        assertNotEquals(token, saved.getValue().getTokenHash());
        assertEquals(NOW.plusHours(1), saved.getValue().getExpiresAt());
        assertEquals("ana@correo.uy", event.getValue().recipient().email());
    }

    @Test
    void aNewLinkCancelsThePreviousOne() {
        PasswordResetToken previous = new PasswordResetToken(ana, "hash-anterior", NOW.minusMinutes(10));
        when(userRepository.findByEmailIgnoreCase("ana@correo.uy")).thenReturn(Optional.of(ana));
        when(tokenRepository.findByUser_IdAndUsedAtIsNullAndExpiresAtAfter(7L, NOW)).thenReturn(List.of(previous));
        when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestReset("ana@correo.uy");

        assertFalse(previous.isUsable(NOW));
    }

    @Test
    void anUnknownEmailOrABlockedAccountGetsNoLinkAndNoError() {
        when(userRepository.findByEmailIgnoreCase("nadie@correo.uy")).thenReturn(Optional.empty());
        service.requestReset("nadie@correo.uy");

        ana.block("Publicidad engañosa", NOW.minusDays(1));
        when(userRepository.findByEmailIgnoreCase("ana@correo.uy")).thenReturn(Optional.of(ana));
        service.requestReset("ana@correo.uy");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void changesThePasswordUsesTheLinkAndClosesOpenSessions() {
        PasswordResetToken resetToken = new PasswordResetToken(ana, SecureTokens.hash("token-valido"), NOW.minusMinutes(5));
        when(tokenRepository.findByTokenHash(SecureTokens.hash("token-valido"))).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NuevaClave123")).thenReturn("hash-nuevo");

        service.resetPassword("token-valido", form("NuevaClave123", "NuevaClave123"));

        assertEquals("hash-nuevo", ana.getPasswordHash());
        assertFalse(resetToken.isUsable(NOW));
        verify(sessionTerminator).terminateSessionsOf(7L);
    }

    @Test
    void anExpiredOrUsedLinkIsRejected() {
        PasswordResetToken expired = new PasswordResetToken(ana, SecureTokens.hash("vencido"), NOW.minusHours(2));
        when(tokenRepository.findByTokenHash(SecureTokens.hash("vencido"))).thenReturn(Optional.of(expired));
        PasswordResetToken used = new PasswordResetToken(ana, SecureTokens.hash("usado"), NOW.minusMinutes(20));
        used.use(NOW.minusMinutes(10));
        when(tokenRepository.findByTokenHash(SecureTokens.hash("usado"))).thenReturn(Optional.of(used));

        assertThrows(IllegalStateException.class, () -> service.resetPassword("vencido", form("NuevaClave123", "NuevaClave123")));
        assertThrows(IllegalStateException.class, () -> service.findUsableToken("usado"));
        assertThrows(IllegalStateException.class, () -> service.findUsableToken("inventado"));
        assertEquals("hash-viejo", ana.getPasswordHash());
    }

    @Test
    void bothPasswordsHaveToMatch() {
        PasswordResetToken resetToken = new PasswordResetToken(ana, SecureTokens.hash("token-valido"), NOW.minusMinutes(5));
        when(tokenRepository.findByTokenHash(SecureTokens.hash("token-valido"))).thenReturn(Optional.of(resetToken));

        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> service.resetPassword("token-valido", form("NuevaClave123", "OtraClave123")));

        assertTrue(error.getFieldErrors().containsKey("confirmPassword"));
        assertTrue(resetToken.isUsable(NOW));
        assertEquals("hash-viejo", ana.getPasswordHash());
    }

    private static PasswordResetForm form(String password, String confirmation) {
        PasswordResetForm form = new PasswordResetForm();
        form.setNewPassword(password);
        form.setConfirmPassword(confirmation);
        return form;
    }
}
