package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserProfileService userProfileService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encoded_old_password");
        testUser.setEmail("test@example.com");
        testUser.setRole(User.Role.USER);
        testUser.setEnabled(true);
    }

    @Test
    void changePassword_ShouldSucceed_WhenValidData() {
        String username = "testuser";
        String currentPassword = "oldpassword";
        String newPassword = "newpassword123";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(newPassword, testUser.getPassword())).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded_new_password");

        assertDoesNotThrow(() -> userProfileService.changePassword(username, currentPassword, newPassword));

        verify(userRepository).save(testUser);
        verify(passwordEncoder).encode(newPassword);
        assertEquals("encoded_new_password", testUser.getPassword());
    }

    @Test
    void changePassword_ShouldFail_WhenUserNotFound() {
        String username = "nonexistent";
        String currentPassword = "oldpassword";
        String newPassword = "newpassword123";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userProfileService.changePassword(username, currentPassword, newPassword));

        assertEquals("User not found: nonexistent", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_ShouldFail_WhenCurrentPasswordIncorrect() {
        String username = "testuser";
        String wrongCurrentPassword = "wrongpassword";
        String newPassword = "newpassword123";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongCurrentPassword, testUser.getPassword())).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userProfileService.changePassword(username, wrongCurrentPassword, newPassword));

        assertEquals("Current password is incorrect", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_ShouldFail_WhenNewPasswordEmpty() {
        String username = "testuser";
        String currentPassword = "oldpassword";
        String newPassword = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userProfileService.changePassword(username, currentPassword, newPassword));

        assertEquals("New password cannot be empty", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_ShouldFail_WhenNewPasswordTooShort() {
        String username = "testuser";
        String currentPassword = "oldpassword";
        String newPassword = "12345";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userProfileService.changePassword(username, currentPassword, newPassword));

        assertEquals("New password must be at least 6 characters long", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_ShouldFail_WhenNewPasswordSameAsCurrent() {
        String username = "testuser";
        String currentPassword = "oldpassword";
        String newPassword = "samepassword";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(newPassword, testUser.getPassword())).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userProfileService.changePassword(username, currentPassword, newPassword));

        assertEquals("New password must be different from current password", exception.getMessage());
        verify(userRepository, never()).save(any());
    }
}