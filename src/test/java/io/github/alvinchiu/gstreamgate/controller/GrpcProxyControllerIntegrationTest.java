package io.github.alvinchiu.gstreamgate.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "grpc.proxy.server.port=0", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development")
class GrpcProxyControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private String loginAsUser() {
        String username = "itest" + System.currentTimeMillis();

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
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl("/api/auth/login"), loginEntity, Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return (String) resp.getBody().get("token");
    }

    @Test
    void listEnabledProxiesWithAuth() {
        String token = loginAsUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                baseUrl("/api/proxy/enabled"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<String, Object>> proxies = resp.getBody();
        assertNotNull(proxies);
        assertFalse(proxies.isEmpty());
    }
}
