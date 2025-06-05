package io.github.alvinchiu.gstreamgate.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        // Inject secret and expiration via reflection
        Field secretField = JwtUtil.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtUtil, "TestSecretKeyThatIsLongEnoughForHS256Algorithm");

        Field expField = JwtUtil.class.getDeclaredField("expiration");
        expField.setAccessible(true);
        expField.set(jwtUtil, 3600000L); // 1 hour
    }

    @Test
    void generateAndValidateToken() {
        UserDetails user = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(user);

        assertNotNull(token);
        assertEquals("testuser", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.validateToken(token, user));
        assertTrue(jwtUtil.isTokenValid(token));
    }
}
