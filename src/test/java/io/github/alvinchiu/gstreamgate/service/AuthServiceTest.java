package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import io.github.alvinchiu.gstreamgate.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private ApplicationContext applicationContext;
    private AuthenticationManager authenticationManager;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        applicationContext = mock(ApplicationContext.class);
        authenticationManager = mock(AuthenticationManager.class);

        when(applicationContext.getBean(AuthenticationManager.class)).thenReturn(authenticationManager);

        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, applicationContext);
    }

    @Test
    void loginReturnsTokenAndUpdatesLastLogin() throws Exception {
        User user = new User("user","pass","user@example.com");
        when(userRepository.findByUsername("user")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("pass", "pass")).thenReturn(true);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateToken(user)).thenReturn("token123");

        Map<String, Object> result = authService.login("user", "pass");

        assertEquals("token123", result.get("token"));
        assertEquals("user", result.get("username"));
        verify(userRepository).updateLastLogin(eq("user"), any(Date.class));
    }

    @Test
    void logoutAddsTokenToBlacklist() {
        when(jwtUtil.isTokenValid("tkn")).thenReturn(true);
        when(jwtUtil.extractUsername("tkn")).thenReturn("user");

        Map<String, Object> result = authService.logout("tkn");

        assertTrue(authService.isTokenBlacklisted("tkn"));
        assertEquals("Logout successful", result.get("message"));
    }

    @Test
    void registerSavesNewUser() {
        when(userRepository.existsByUsername("user")).thenReturn(false);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = authService.register("user","pass","user@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", captor.getValue().getPassword());
        assertEquals("User registered successfully", result.get("message"));
    }

    @Test
    void validateTokenHandlesValidAndInvalid() {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUsername("good")).thenReturn("user");
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);

        Map<String, Object> ok = authService.validateToken("good");
        Map<String, Object> bad = authService.validateToken("bad");

        assertEquals(true, ok.get("valid"));
        assertEquals("user", ok.get("username"));
        assertEquals(false, bad.get("valid"));
    }
}
