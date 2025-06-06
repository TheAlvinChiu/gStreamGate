package io.github.alvinchiu.gstreamgate.controller;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.service.GrpcProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing gRPC proxy mappings with enhanced security
 * 所有操作都需要適當的JWT token和權限
 */
@RestController
@RequestMapping("/api/proxy")
public class GrpcProxyController {
    private static final Logger logger = LoggerFactory.getLogger(GrpcProxyController.class);

    private final GrpcProxyService grpcProxyService;

    @Autowired
    public GrpcProxyController(GrpcProxyService grpcProxyService) {
        this.grpcProxyService = grpcProxyService;
        logger.info("GrpcProxyController initialized with enhanced security");
    }

    @PostConstruct
    public void init() {
        logger.info("GrpcProxyController @PostConstruct - Controller ready with JWT protection");
        logger.info("Service: {}", grpcProxyService != null ? "OK" : "NULL");
    }

    /**
     * 測試端點 - 需要認證
     */
    @GetMapping("/test")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> test() {
        String currentUser = getCurrentUsername();
        logger.info("Test endpoint called by user: {}", currentUser);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "GrpcProxyController is working");
        response.put("user", currentUser);
        response.put("timestamp", new Date());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all proxy mappings - 需要認證，USER和ADMIN都可存取
     */
    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<GrpcProxyMap>> getAllProxies() {
        String currentUser = getCurrentUsername();
        logger.info("GET /api/proxy - Getting all proxy mappings, requested by: {}", currentUser);

        try {
            List<GrpcProxyMap> proxies = grpcProxyService.getAllProxies();
            logger.info("Found {} proxy mappings for user: {}", proxies.size(), currentUser);
            return ResponseEntity.ok(proxies);
        } catch (Exception e) {
            logger.error("Error getting all proxies for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get enabled proxy mappings - 需要認證，USER和ADMIN都可存取
     */
    @GetMapping("/enabled")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<GrpcProxyMap>> getEnabledProxies() {
        String currentUser = getCurrentUsername();
        logger.info("GET /api/proxy/enabled - Getting enabled proxy mappings, requested by: {}", currentUser);

        try {
            List<GrpcProxyMap> proxies = grpcProxyService.getEnabledProxies();
            logger.info("Found {} enabled proxy mappings for user: {}", proxies.size(), currentUser);
            return ResponseEntity.ok(proxies);
        } catch (Exception e) {
            logger.error("Error getting enabled proxies for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get a specific proxy mapping by ID - 僅ADMIN可存取
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrpcProxyMap> getProxyById(@PathVariable Long id) {
        String currentUser = getCurrentUsername();
        logger.info("GET /api/proxy/{} - Getting proxy by ID, requested by admin: {}", id, currentUser);

        try {
            GrpcProxyMap proxy = grpcProxyService.getProxyById(id);
            if (proxy != null) {
                logger.info("Found proxy mapping: {} for admin: {}", proxy.getProxyHostName(), currentUser);
                return ResponseEntity.ok(proxy);
            } else {
                logger.warn("Proxy mapping not found with ID: {} for admin: {}", id, currentUser);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error getting proxy by ID {} for admin {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a new proxy mapping - 僅ADMIN可操作
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrpcProxyMap> createProxy(@RequestBody GrpcProxyMap proxyMap) {
        String currentUser = getCurrentUsername();
        logger.info("POST /api/proxy - Creating new proxy mapping: {}, by admin: {}",
                proxyMap.getProxyHostName(), currentUser);

        try {
            GrpcProxyMap savedProxy = grpcProxyService.createProxy(proxyMap, currentUser);
            logger.info("Created proxy mapping with ID: {} by admin: {}", savedProxy.getProxyMapId(), currentUser);
            return new ResponseEntity<>(savedProxy, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error creating proxy mapping by admin {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing proxy mapping - 僅ADMIN可操作
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrpcProxyMap> updateProxy(@PathVariable Long id, @RequestBody GrpcProxyMap proxyMap) {
        String currentUser = getCurrentUsername();
        logger.info("PUT /api/proxy/{} - Updating proxy mapping by admin: {}", id, currentUser);

        try {
            GrpcProxyMap updatedProxy = grpcProxyService.updateProxy(id, proxyMap, currentUser);
            if (updatedProxy != null) {
                logger.info("Updated proxy mapping: {} by admin: {}",
                        updatedProxy.getProxyHostName(), currentUser);
                return ResponseEntity.ok(updatedProxy);
            } else {
                logger.warn("Proxy mapping not found for update with ID: {} by admin: {}", id, currentUser);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error updating proxy mapping {} by admin {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enable or disable a proxy mapping - 僅ADMIN可操作
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateProxyStatus(@PathVariable Long id, @RequestParam boolean enable) {
        String currentUser = getCurrentUsername();
        logger.info("PATCH /api/proxy/{}/status - {} proxy mapping by admin: {}",
                id, enable ? "Enabling" : "Disabling", currentUser);

        try {
            GrpcProxyMap updatedProxy = grpcProxyService.updateProxyStatus(id, enable, currentUser);
            if (updatedProxy != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Proxy " + (enable ? "enabled" : "disabled") + " successfully");
                response.put("proxy", updatedProxy);
                response.put("admin", currentUser);

                logger.info("Proxy {} {} by admin {}: {}", updatedProxy.getProxyMapId(),
                        enable ? "enabled" : "disabled", currentUser, updatedProxy.getProxyHostName());

                return ResponseEntity.ok(response);
            } else {
                logger.warn("Proxy mapping not found for status update with ID: {} by admin: {}",
                        id, currentUser);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error updating proxy status {} by admin {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a proxy mapping - 僅ADMIN可操作
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProxy(@PathVariable Long id) {
        String currentUser = getCurrentUsername();
        logger.info("DELETE /api/proxy/{} - Deleting proxy mapping by admin: {}", id, currentUser);

        try {
            boolean deleted = grpcProxyService.deleteProxy(id);
            if (deleted) {
                logger.info("Deleted proxy mapping with ID: {} by admin: {}", id, currentUser);
                return ResponseEntity.noContent().<Void>build();
            } else {
                logger.warn("Proxy mapping not found for deletion with ID: {} by admin: {}",
                        id, currentUser);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error deleting proxy mapping {} by admin {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Refresh all proxy mappings - 僅ADMIN可操作
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> refreshProxies() {
        String currentUser = getCurrentUsername();
        logger.info("POST /api/proxy/refresh - Refreshing all proxy mappings by admin: {}", currentUser);

        try {
            grpcProxyService.refreshProxies();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All proxy mappings refreshed successfully");
            response.put("admin", currentUser);
            response.put("timestamp", new Date());

            logger.info("All proxy mappings refreshed successfully by admin: {}", currentUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error refreshing proxy mappings by admin {}: {}", currentUser, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh proxy mappings: " + e.getMessage());
            errorResponse.put("admin", currentUser);
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get active proxy hostnames - 需要認證，USER和ADMIN都可存取
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getActiveProxies() {
        String currentUser = getCurrentUsername();
        logger.info("GET /api/proxy/active - Getting active proxy hostnames by user: {}", currentUser);

        try {
            List<String> activeHostnames = grpcProxyService.getActiveProxyHostnames();

            Map<String, Object> response = new HashMap<>();
            response.put("count", activeHostnames.size());
            response.put("hostnames", activeHostnames);
            response.put("user", currentUser);
            response.put("timestamp", new Date());

            logger.info("Found {} active proxy hostnames for user: {}", activeHostnames.size(), currentUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting active proxies for user {}: {}", currentUser, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("hostnames", List.of());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("user", currentUser);
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 健康檢查端點 - 需要認證
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> health() {
        String currentUser = getCurrentUsername();
        logger.info("GET /api/proxy/health - Health check by user: {}", currentUser);

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "UP");
            response.put("controller", "GrpcProxyController");
            response.put("service", grpcProxyService != null ? "OK" : "FAIL");
            response.put("user", currentUser);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Health check failed for user {}: {}", currentUser, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("user", currentUser);
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    /**
     * 獲取當前登入用戶名
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
}