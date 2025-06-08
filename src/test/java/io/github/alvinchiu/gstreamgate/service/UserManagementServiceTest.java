package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.controller.UserManagementController;
import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserManagementService userManagementService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setRole(User.Role.USER);
        testUser.setEnabled(true);
        testUser.setCreatedDate(new Date());
    }

    @Test
    void getAllUsers_ShouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = Arrays.asList(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, 1);

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<User> result = userManagementService.getAllUsers(pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(testUser, result.getContent().get(0));
        verify(userRepository).findAll(pageable);
    }

    @Test
    void getUserById_ShouldReturnUser() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        User result = userManagementService.getUserById(userId);

        assertEquals(testUser, result);
        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_WithNonExistentUser_ShouldThrowException() {
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.getUserById(userId));

        assertEquals("User not found with id: " + userId, exception.getMessage());
    }

    @Test
    void createUser_ShouldCreateNewUser() {
        String username = "newuser";
        String password = "password";
        String email = "newuser@example.com";
        User.Role role = User.Role.USER;

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userManagementService.createUser(username, password, email, role);

        assertEquals(testUser, result);
        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_WithExistingUsername_ShouldThrowException() {
        String username = "existinguser";
        String password = "password";
        String email = "newuser@example.com";
        User.Role role = User.Role.USER;

        when(userRepository.existsByUsername(username)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.createUser(username, password, email, role));

        assertEquals("Username already exists: " + username, exception.getMessage());
        verify(userRepository).existsByUsername(username);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithExistingEmail_ShouldThrowException() {
        String username = "newuser";
        String password = "password";
        String email = "existing@example.com";
        User.Role role = User.Role.USER;

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.createUser(username, password, email, role));

        assertEquals("Email already exists: " + email, exception.getMessage());
        verify(userRepository).existsByEmail(email);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_ShouldUpdateUser() {
        Long userId = 1L;
        UserManagementController.UpdateUserRequest request = new UserManagementController.UpdateUserRequest();
        request.setUsername("updateduser");
        request.setEmail("updated@example.com");
        request.setRole(User.Role.ADMIN);
        request.setEnabled(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("updateduser")).thenReturn(false);
        when(userRepository.existsByEmail("updated@example.com")).thenReturn(false);
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userManagementService.updateUser(userId, request);

        assertEquals(testUser, result);
        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
    }

    @Test
    void deleteUser_ShouldDeleteUser() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(2L);

        userManagementService.deleteUser(userId);

        verify(userRepository).findById(userId);
        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteUser_LastAdmin_ShouldThrowException() {
        Long userId = 1L;
        testUser.setRole(User.Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(1L);

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.deleteUser(userId));

        assertEquals("Cannot delete the last admin user", exception.getMessage());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void toggleUserStatus_ShouldEnableUser() {
        Long userId = 1L;
        testUser.setEnabled(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userManagementService.toggleUserStatus(userId, true);

        assertTrue(result.isEnabled());
        verify(userRepository).save(testUser);
    }

    @Test
    void toggleUserStatus_DisableLastAdmin_ShouldThrowException() {
        Long userId = 1L;
        testUser.setRole(User.Role.ADMIN);
        testUser.setEnabled(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.countByRoleAndEnabled(User.Role.ADMIN, true)).thenReturn(1L);

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.toggleUserStatus(userId, false));

        assertEquals("Cannot disable the last enabled admin user", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRole_ShouldUpdateRole() {
        Long userId = 1L;
        User.Role newRole = User.Role.ADMIN;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userManagementService.updateUserRole(userId, newRole);

        assertEquals(newRole, result.getRole());
        verify(userRepository).save(testUser);
    }

    @Test
    void updateUserRole_ChangeLastAdmin_ShouldThrowException() {
        Long userId = 1L;
        testUser.setRole(User.Role.ADMIN);
        User.Role newRole = User.Role.USER;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(1L);

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> userManagementService.updateUserRole(userId, newRole));

        assertEquals("Cannot change role of the last admin user", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void searchUsers_ShouldReturnSearchResults() {
        String keyword = "test";
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = Arrays.asList(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, 1);

        when(userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, pageable)).thenReturn(userPage);

        Page<User> result = userManagementService.searchUsers(keyword, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(testUser, result.getContent().get(0));
        verify(userRepository).findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, pageable);
    }

    @Test
    void getTotalUserCount_ShouldReturnCount() {
        when(userRepository.count()).thenReturn(5L);

        long result = userManagementService.getTotalUserCount();

        assertEquals(5L, result);
        verify(userRepository).count();
    }

    @Test
    void getActiveUserCount_ShouldReturnCount() {
        when(userRepository.countByEnabled(true)).thenReturn(3L);

        long result = userManagementService.getActiveUserCount();

        assertEquals(3L, result);
        verify(userRepository).countByEnabled(true);
    }

    @Test
    void getAdminUserCount_ShouldReturnCount() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(2L);

        long result = userManagementService.getAdminUserCount();

        assertEquals(2L, result);
        verify(userRepository).countByRole(User.Role.ADMIN);
    }
}