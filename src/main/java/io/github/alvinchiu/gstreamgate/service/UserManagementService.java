package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.controller.UserManagementController;
import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class UserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Page<User> getAllUsers(Pageable pageable) {
        logger.debug("Retrieving all users with pagination: page={}, size={}", 
                    pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findAll(pageable);
    }

    public User getUserById(Long id) {
        logger.debug("Retrieving user by id: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Transactional
    public User createUser(String username, String password, String email, User.Role role) {
        logger.info("Creating new user: {}", username);

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role != null ? role : User.Role.USER);
        user.setEnabled(true);
        user.setCreatedDate(new Date());

        User savedUser = userRepository.save(user);
        logger.info("User created successfully: {} with role: {}", username, savedUser.getRole());
        
        return savedUser;
    }

    @Transactional
    public User updateUser(Long id, UserManagementController.UpdateUserRequest request) {
        logger.info("Updating user: {}", id);

        User user = getUserById(id);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication != null ? authentication.getName() : null;

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new RuntimeException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null) {
            if (currentUsername != null && user.getUsername().equals(currentUsername) && user.getRole() == User.Role.ADMIN && request.getRole() != User.Role.ADMIN) {
                throw new RuntimeException("Admin users cannot demote themselves");
            }
            
            if (user.getRole() == User.Role.ADMIN && request.getRole() != User.Role.ADMIN) {
                long adminCount = userRepository.countByRole(User.Role.ADMIN);
                if (adminCount <= 1) {
                    throw new RuntimeException("Cannot change role of the last admin user");
                }
            }
            
            user.setRole(request.getRole());
        }

        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        User updatedUser = userRepository.save(user);
        logger.info("User updated successfully: {}", updatedUser.getUsername());
        
        return updatedUser;
    }

    @Transactional
    public void deleteUser(Long id) {
        logger.info("Deleting user: {}", id);

        User user = getUserById(id);
        
        if (user.getRole() == User.Role.ADMIN) {
            long adminCount = userRepository.countByRole(User.Role.ADMIN);
            if (adminCount <= 1) {
                throw new RuntimeException("Cannot delete the last admin user");
            }
        }

        userRepository.delete(user);
        logger.info("User deleted successfully: {}", user.getUsername());
    }

    @Transactional
    public User toggleUserStatus(Long id, boolean enabled) {
        logger.info("Setting user {} status to: {}", id, enabled);

        User user = getUserById(id);
        
        if (!enabled && user.getRole() == User.Role.ADMIN) {
            long enabledAdminCount = userRepository.countByRoleAndEnabled(User.Role.ADMIN, true);
            if (enabledAdminCount <= 1) {
                throw new RuntimeException("Cannot disable the last enabled admin user");
            }
        }

        user.setEnabled(enabled);
        User updatedUser = userRepository.save(user);
        
        logger.info("User status updated successfully: {} -> enabled: {}", 
                   updatedUser.getUsername(), updatedUser.isEnabled());
        
        return updatedUser;
    }

    @Transactional
    public User updateUserRole(Long id, User.Role newRole) {
        logger.info("Updating user {} role to: {}", id, newRole);

        User user = getUserById(id);
        User.Role oldRole = user.getRole();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication != null ? authentication.getName() : null;
        
        if (currentUsername != null && user.getUsername().equals(currentUsername) && oldRole == User.Role.ADMIN && newRole != User.Role.ADMIN) {
            throw new RuntimeException("Admin users cannot demote themselves");
        }

        if (oldRole == User.Role.ADMIN && newRole != User.Role.ADMIN) {
            long adminCount = userRepository.countByRole(User.Role.ADMIN);
            if (adminCount <= 1) {
                throw new RuntimeException("Cannot change role of the last admin user");
            }
        }

        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        
        logger.info("User role updated successfully: {} -> {} to {}", 
                   updatedUser.getUsername(), oldRole, newRole);
        
        return updatedUser;
    }

    public Page<User> searchUsers(String keyword, Pageable pageable) {
        logger.debug("Searching users with keyword: '{}', page: {}, size: {}", 
                    keyword, pageable.getPageNumber(), pageable.getPageSize());
        
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, pageable);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    public long getActiveUserCount() {
        return userRepository.countByEnabled(true);
    }

    public long getAdminUserCount() {
        return userRepository.countByRole(User.Role.ADMIN);
    }
}