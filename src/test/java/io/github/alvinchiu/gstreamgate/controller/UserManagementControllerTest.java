package io.github.alvinchiu.gstreamgate.controller;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserManagementControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private UserManagementController userManagementController;

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

        when(userManagementService.getAllUsers(pageable)).thenReturn(userPage);

        ResponseEntity<Map<String, Object>> response = userManagementController.getAllUsers(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, ((List<?>) response.getBody().get("users")).size());
        assertEquals(1L, response.getBody().get("totalElements"));
        assertEquals(1, response.getBody().get("totalPages"));
    }

    @Test
    void getUserById_ShouldReturnUser() {
        Long userId = 1L;
        when(userManagementService.getUserById(userId)).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.getUserById(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void createUser_ShouldReturnCreatedUser() {
        UserManagementController.CreateUserRequest request = new UserManagementController.CreateUserRequest();
        request.setUsername("newuser");
        request.setPassword("password");
        request.setEmail("newuser@example.com");
        request.setRole(User.Role.USER);

        when(userManagementService.createUser(
                eq("newuser"), 
                eq("password"), 
                eq("newuser@example.com"), 
                eq(User.Role.USER)
        )).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User created successfully", response.getBody().get("message"));
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void updateUser_ShouldReturnUpdatedUser() {
        Long userId = 1L;
        UserManagementController.UpdateUserRequest request = new UserManagementController.UpdateUserRequest();
        request.setUsername("updateduser");
        request.setEmail("updated@example.com");
        request.setRole(User.Role.ADMIN);
        request.setEnabled(true);

        when(userManagementService.updateUser(eq(userId), any())).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.updateUser(userId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User updated successfully", response.getBody().get("message"));
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void deleteUser_ShouldReturnSuccessMessage() {
        Long userId = 1L;
        doNothing().when(userManagementService).deleteUser(userId);

        ResponseEntity<Map<String, Object>> response = userManagementController.deleteUser(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User deleted successfully", response.getBody().get("message"));
        verify(userManagementService).deleteUser(userId);
    }

    @Test
    void enableUser_ShouldReturnEnabledUser() {
        Long userId = 1L;
        when(userManagementService.toggleUserStatus(userId, true)).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.enableUser(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User enabled successfully", response.getBody().get("message"));
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void disableUser_ShouldReturnDisabledUser() {
        Long userId = 1L;
        when(userManagementService.toggleUserStatus(userId, false)).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.disableUser(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User disabled successfully", response.getBody().get("message"));
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void updateUserRole_ShouldReturnUpdatedUser() {
        Long userId = 1L;
        UserManagementController.UpdateRoleRequest request = new UserManagementController.UpdateRoleRequest();
        request.setRole(User.Role.ADMIN);

        when(userManagementService.updateUserRole(userId, User.Role.ADMIN)).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = userManagementController.updateUserRole(userId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User role updated successfully", response.getBody().get("message"));
        assertEquals(testUser, response.getBody().get("user"));
    }

    @Test
    void searchUsers_ShouldReturnSearchResults() {
        String keyword = "test";
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = Arrays.asList(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.searchUsers(keyword, pageable)).thenReturn(userPage);

        ResponseEntity<Map<String, Object>> response = userManagementController.searchUsers(keyword, pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, ((List<?>) response.getBody().get("users")).size());
        assertEquals(keyword, response.getBody().get("keyword"));
    }

    @Test
    void createUser_WithException_ShouldReturnBadRequest() {
        UserManagementController.CreateUserRequest request = new UserManagementController.CreateUserRequest();
        request.setUsername("existinguser");
        request.setPassword("password");
        request.setEmail("existing@example.com");
        request.setRole(User.Role.USER);

        when(userManagementService.createUser(
                eq("existinguser"), 
                eq("password"), 
                eq("existing@example.com"), 
                eq(User.Role.USER)
        )).thenThrow(new RuntimeException("Username already exists"));

        ResponseEntity<Map<String, Object>> response = userManagementController.createUser(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Username already exists", response.getBody().get("error"));
    }

    @Test
    void getUserById_WithNonExistentUser_ShouldReturnNotFound() {
        Long userId = 999L;
        when(userManagementService.getUserById(userId))
                .thenThrow(new RuntimeException("User not found with id: " + userId));

        ResponseEntity<Map<String, Object>> response = userManagementController.getUserById(userId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User not found with id: " + userId, response.getBody().get("error"));
    }
}