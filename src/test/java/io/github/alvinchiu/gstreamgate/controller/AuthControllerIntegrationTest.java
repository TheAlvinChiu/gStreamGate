package io.github.alvinchiu.gstreamgate.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "grpc.proxy.server.port=0", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development")
class AuthControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void loginAndFetchCurrentUser() {
        String username = "ituser" + System.currentTimeMillis();

        Map<String, String> register = new HashMap<>();
        register.put("username", username);
        register.put("password", "pass");
        register.put("email", username + "@ex.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> regEntity = new HttpEntity<>(register, headers);
        ResponseEntity<Map> regResp = restTemplate.postForEntity(baseUrl("/api/auth/register"), regEntity, Map.class);
        assertEquals(HttpStatus.CREATED, regResp.getStatusCode());

        Map<String, String> login = new HashMap<>();
        login.put("username", username);
        login.put("password", "pass");

        HttpEntity<Map<String, String>> loginEntity = new HttpEntity<>(login, headers);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(baseUrl("/api/auth/login"), loginEntity, Map.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        String token = (String) loginResp.getBody().get("token");
        assertNotNull(token);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders);
        ResponseEntity<Map> meResp = restTemplate.exchange(baseUrl("/api/auth/me"), HttpMethod.GET, entity, Map.class);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertEquals(username, meResp.getBody().get("username"));
        assertEquals(true, meResp.getBody().get("authenticated"));
    }
}
