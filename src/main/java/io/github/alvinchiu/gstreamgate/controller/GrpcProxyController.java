package io.github.alvinchiu.gstreamgate.controller;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.manager.DynamicGrpcProxyManager;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing gRPC proxy mappings
 * 添加初始化日志和測試端點
 */
@RestController
@RequestMapping("/api/proxy")
@CrossOrigin(origins = "*") // 允許跨域請求
public class GrpcProxyController {
    private static final Logger logger = LoggerFactory.getLogger(GrpcProxyController.class);

    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final DynamicGrpcProxyManager proxyManager;

    @Autowired
    public GrpcProxyController(GrpcProxyMapRepository grpcProxyMapRepository,
                               DynamicGrpcProxyManager proxyManager) {
        this.grpcProxyMapRepository = grpcProxyMapRepository;
        this.proxyManager = proxyManager;
        logger.info("GrpcProxyController initialized");
    }

    @PostConstruct
    public void init() {
        logger.info("GrpcProxyController @PostConstruct - Controller is ready");
        logger.info("Repository: {}", grpcProxyMapRepository != null ? "OK" : "NULL");
        logger.info("ProxyManager: {}", proxyManager != null ? "OK" : "NULL");
    }

    /**
     * 測試端點 - 確認 Controller 工作正常
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        logger.info("Test endpoint called");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "GrpcProxyController is working");
        response.put("timestamp", new Date());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all proxy mappings
     *
     * @return List of all proxy mappings
     */
    @GetMapping
    public ResponseEntity<List<GrpcProxyMap>> getAllProxies() {
        logger.info("GET /api/proxy - Getting all proxy mappings");
        try {
            List<GrpcProxyMap> proxies = grpcProxyMapRepository.findAll();
            logger.info("Found {} proxy mappings", proxies.size());
            return ResponseEntity.ok(proxies);
        } catch (Exception e) {
            logger.error("Error getting all proxies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get enabled proxy mappings
     *
     * @return List of enabled proxy mappings
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<GrpcProxyMap>> getEnabledProxies() {
        logger.info("GET /api/proxy/enabled - Getting enabled proxy mappings");
        try {
            List<GrpcProxyMap> proxies = grpcProxyMapRepository.findByEnable("Y");
            logger.info("Found {} enabled proxy mappings", proxies.size());
            return ResponseEntity.ok(proxies);
        } catch (Exception e) {
            logger.error("Error getting enabled proxies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get a specific proxy mapping by ID
     *
     * @param id The proxy mapping ID
     * @return The proxy mapping or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<GrpcProxyMap> getProxyById(@PathVariable Long id) {
        logger.info("GET /api/proxy/{} - Getting proxy by ID", id);
        try {
            return grpcProxyMapRepository.findById(id)
                    .map(proxy -> {
                        logger.info("Found proxy mapping: {}", proxy.getProxyHostName());
                        return ResponseEntity.ok(proxy);
                    })
                    .orElseGet(() -> {
                        logger.warn("Proxy mapping not found with ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            logger.error("Error getting proxy by ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a new proxy mapping
     *
     * @param proxyMap The proxy mapping to create
     * @return The created proxy mapping
     */
    @PostMapping
    public ResponseEntity<GrpcProxyMap> createProxy(@RequestBody GrpcProxyMap proxyMap) {
        logger.info("POST /api/proxy - Creating new proxy mapping: {}", proxyMap.getProxyHostName());
        try {
            // Set creation metadata
            proxyMap.setCreateDateTime(new Date());
            proxyMap.setCreateUser("API");

            // Save to database
            GrpcProxyMap savedProxy = grpcProxyMapRepository.save(proxyMap);
            logger.info("Created proxy mapping with ID: {}", savedProxy.getProxyMapId());

            // Register with proxy manager if enabled
            if ("Y".equals(savedProxy.getEnable())) {
                proxyManager.addProxyMapping(savedProxy);
                logger.info("Registered proxy mapping with manager: {}", savedProxy.getProxyHostName());
            }

            return new ResponseEntity<>(savedProxy, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error creating proxy mapping: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing proxy mapping
     *
     * @param id The proxy mapping ID
     * @param proxyMap The updated proxy mapping
     * @return The updated proxy mapping or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<GrpcProxyMap> updateProxy(@PathVariable Long id, @RequestBody GrpcProxyMap proxyMap) {
        logger.info("PUT /api/proxy/{} - Updating proxy mapping", id);
        try {
            return grpcProxyMapRepository.findById(id)
                    .map(existingProxy -> {
                        // Update fields
                        existingProxy.setServiceName(proxyMap.getServiceName());
                        existingProxy.setProxyHostName(proxyMap.getProxyHostName());
                        existingProxy.setTargetHostName(proxyMap.getTargetHostName());
                        existingProxy.setTargetPort(proxyMap.getTargetPort());
                        existingProxy.setConnectTimeoutMs(proxyMap.getConnectTimeoutMs());
                        existingProxy.setSendTimeoutMs(proxyMap.getSendTimeoutMs());
                        existingProxy.setReadTimeoutMs(proxyMap.getReadTimeoutMs());
                        existingProxy.setSecureMode(proxyMap.getSecureMode());
                        existingProxy.setServerCertContent(proxyMap.getServerCertContent());
                        existingProxy.setServerKeyContent(proxyMap.getServerKeyContent());
                        existingProxy.setAutoTrustUpstreamCerts(proxyMap.getAutoTrustUpstreamCerts());
                        existingProxy.setTrustedCertsContent(proxyMap.getTrustedCertsContent());
                        existingProxy.setEnable(proxyMap.getEnable());

                        // Set update metadata
                        existingProxy.setUpdateDateTime(new Date());
                        existingProxy.setUpdateUser("API");

                        // Save to database
                        GrpcProxyMap updatedProxy = grpcProxyMapRepository.save(existingProxy);
                        logger.info("Updated proxy mapping: {}", updatedProxy.getProxyHostName());

                        // Update proxy manager
                        proxyManager.updateProxyMapping(updatedProxy);

                        return ResponseEntity.ok(updatedProxy);
                    })
                    .orElseGet(() -> {
                        logger.warn("Proxy mapping not found for update with ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            logger.error("Error updating proxy mapping {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enable or disable a proxy mapping
     *
     * @param id The proxy mapping ID
     * @param enable Whether to enable (true) or disable (false) the proxy
     * @return Success message or 404 if not found
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateProxyStatus(@PathVariable Long id, @RequestParam boolean enable) {
        logger.info("PATCH /api/proxy/{}/status - {} proxy mapping", id, enable ? "Enabling" : "Disabling");
        try {
            return grpcProxyMapRepository.findById(id)
                    .map(existingProxy -> {
                        // Update enable status
                        existingProxy.setEnable(enable ? "Y" : "N");

                        // Set update metadata
                        existingProxy.setUpdateDateTime(new Date());
                        existingProxy.setUpdateUser("API");

                        // Save to database
                        GrpcProxyMap updatedProxy = grpcProxyMapRepository.save(existingProxy);

                        // Update proxy manager
                        proxyManager.updateProxyMapping(updatedProxy);

                        // Return success message
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "Proxy " + (enable ? "enabled" : "disabled") + " successfully");
                        response.put("proxy", updatedProxy);

                        logger.info("Proxy {} {}: {}", updatedProxy.getProxyMapId(),
                                enable ? "enabled" : "disabled", updatedProxy.getProxyHostName());

                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        logger.warn("Proxy mapping not found for status update with ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            logger.error("Error updating proxy status {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a proxy mapping
     *
     * @param id The proxy mapping ID
     * @return No content or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProxy(@PathVariable Long id) {
        logger.info("DELETE /api/proxy/{} - Deleting proxy mapping", id);
        try {
            return grpcProxyMapRepository.findById(id)
                    .map(proxy -> {
                        // Delete from database
                        grpcProxyMapRepository.delete(proxy);

                        // Delete from proxy manager
                        proxyManager.deleteProxyMapping(proxy);

                        logger.info("Deleted proxy mapping: {}", proxy.getProxyHostName());
                        return ResponseEntity.noContent().<Void>build();
                    })
                    .orElseGet(() -> {
                        logger.warn("Proxy mapping not found for deletion with ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            logger.error("Error deleting proxy mapping {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Refresh all proxy mappings
     *
     * @return Success message
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshProxies() {
        logger.info("POST /api/proxy/refresh - Refreshing all proxy mappings");
        try {
            proxyManager.refreshProxyMappings();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All proxy mappings refreshed successfully");
            response.put("timestamp", new Date());

            logger.info("All proxy mappings refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error refreshing proxy mappings: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refresh proxy mappings: " + e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get active proxy hostnames
     *
     * @return List of active proxy hostnames
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveProxies() {
        logger.info("GET /api/proxy/active - Getting active proxy hostnames");
        try {
            List<String> activeHostnames = proxyManager.getActiveProxyHostnames();

            Map<String, Object> response = new HashMap<>();
            response.put("count", activeHostnames.size());
            response.put("hostnames", activeHostnames);
            response.put("timestamp", new Date());

            logger.info("Found {} active proxy hostnames", activeHostnames.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting active proxies: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("hostnames", List.of());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 健康檢查端點
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.info("GET /api/proxy/health - Health check");
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "UP");
            response.put("controller", "GrpcProxyController");
            response.put("repository", grpcProxyMapRepository != null ? "OK" : "FAIL");
            response.put("proxyManager", proxyManager != null ? "OK" : "FAIL");
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }
}