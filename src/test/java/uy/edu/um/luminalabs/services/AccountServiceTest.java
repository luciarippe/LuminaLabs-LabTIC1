package uy.edu.um.luminalabs.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.luminalabs.dto.PasswordChangeForm;
import uy.edu.um.luminalabs.dto.ProfileForm;
import uy.edu.um.luminalabs.entities.Tourist;
import uy.edu.um.luminalabs.entities.User;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.UserRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// "Mi cuenta" (turistas y administradores): datos propios y cambio de contrasena
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, passwordEncoder);
    }

    private User existingUser() {
        Tourist user = Tourist.builder().firstName("Ana").lastName("Pérez").username("anaperez")
                .email("ana@mail.com").passwordHash("hash-actual").build();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    private PasswordChangeForm passwordForm(String current, String newPassword, String confirm) {
        PasswordChangeForm passwordForm = new PasswordChangeForm();
        passwordForm.setCurrentPassword(current);
        passwordForm.setNewPassword(newPassword);
        passwordForm.setConfirmPassword(confirm);
        return passwordForm;
    }

    @Test
    void updatesOwnProfile() {
        User user = existingUser();
        ProfileForm profile = new ProfileForm();
        profile.setFirstName(" Ana María ");
        profile.setLastName("Pérez");
        profile.setBirthDate(LocalDate.of(1990, 5, 10));
        profile.setEmail(" AnaMaria@Mail.com ");

        service.updateProfile(7L, profile);

        assertEquals("Ana María", user.getFirstName());
        assertEquals("anamaria@mail.com", user.getEmail());
    }

    @Test
    void rejectsEmailOfAnotherAccount() {
        existingUser();
        ProfileForm profile = new ProfileForm();
        profile.setFirstName("Ana");
        profile.setLastName("Pérez");
        profile.setBirthDate(LocalDate.of(1990, 5, 10));
        profile.setEmail("otro@mail.com");
        when(userRepository.existsByEmailIgnoreCase("otro@mail.com")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.updateProfile(7L, profile));

        assertTrue(exception.getFieldErrors().containsKey("email"));
    }

    @Test
    void changesPasswordWhenCurrentOneIsCorrect() {
        User user = existingUser();
        when(passwordEncoder.matches("actual1234", "hash-actual")).thenReturn(true);
        when(passwordEncoder.encode("nueva12345")).thenReturn("hash-nuevo");

        service.changePassword(7L, passwordForm("actual1234", "nueva12345", "nueva12345"));

        assertEquals("hash-nuevo", user.getPasswordHash());
    }

    @Test
    void rejectsWrongCurrentPassword() {
        User user = existingUser();
        when(passwordEncoder.matches("equivocada", "hash-actual")).thenReturn(false);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.changePassword(7L, passwordForm("equivocada", "nueva12345", "nueva12345")));

        assertTrue(exception.getFieldErrors().containsKey("currentPassword"));
        assertEquals("hash-actual", user.getPasswordHash());
    }

    @Test
    void rejectsNewPasswordEqualToCurrentOne() {
        existingUser();
        when(passwordEncoder.matches("actual1234", "hash-actual")).thenReturn(true);

        FieldValidationException exception = assertThrows(FieldValidationException.class,
                () -> service.changePassword(7L, passwordForm("actual1234", "actual1234", "actual1234")));

        assertTrue(exception.getFieldErrors().containsKey("newPassword"));
    }
}
