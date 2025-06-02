package io.github.alvinchiu.gstreamgate.manager;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.event.ProxyConfigChangedEvent;
import io.github.alvinchiu.gstreamgate.handler.HostBasedHandlerRegistry;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import io.github.alvinchiu.gstreamgate.security.TlsCertificateManager;
import io.grpc.HandlerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Dynamic gRPC proxy manager, responsible for managing proxy channels and their configurations.
 * Supports dynamically adding, updating, and deleting proxy mappings.
 */
@Component
public class DynamicGrpcProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(DynamicGrpcProxyManager.class);

    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final TlsCertificateManager tlsCertificateManager;
    private final Map<String, ManagedChannel> proxyChannels = new ConcurrentHashMap<>();
    private final Map<String, GrpcProxyMap> activeProxyMappings = new ConcurrentHashMap<>();
    private HostBasedHandlerRegistry handlerRegistry;

    // Spring event publisher
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructor that injects required dependencies
     */
    @Autowired
    public DynamicGrpcProxyManager(GrpcProxyMapRepository grpcProxyMapRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   TlsCertificateManager tlsCertificateManager) {
        this.grpcProxyMapRepository = grpcProxyMapRepository;
        this.eventPublisher = eventPublisher;
        this.tlsCertificateManager = tlsCertificateManager;
    }

    /**
     * Initialization method, executed when Spring container starts
     */
    @PostConstruct
    public void initialize() {
        logger.debug("Initializing dynamic gRPC proxy manager");
        try {
            refreshProxyMappings();
        } catch (Exception e) {
            logger.error("Error initializing dynamic proxy manager: " + e.getMessage(), e);
            // Create an empty handler registry to ensure the application can start
            handlerRegistry = new HostBasedHandlerRegistry(new HashMap<>());
        }
    }

    /**
     * Refresh all proxy mappings from the database
     * This method is executed on application startup or when a manual refresh is triggered
     */
    public synchronized void refreshProxyMappings() {
        logger.debug("Refreshing proxy mappings from database");

        try {
            // Get all enabled proxy mappings
            List<GrpcProxyMap> enabledMappings = grpcProxyMapRepository.findByEnable("Y");
            logger.debug("Found " + enabledMappings.size() + " enabled proxy mappings");

            // Remove channels that are no longer enabled
            List<String> newProxyHostnames = enabledMappings.stream()
                    .map(GrpcProxyMap::getProxyHostName)
                    .collect(Collectors.toList());

            // Find the hostnames to remove
            List<String> hostnamesForRemoval = proxyChannels.keySet().stream()
                    .filter(hostname -> !newProxyHostnames.contains(hostname))
                    .collect(Collectors.toList());

            // Remove channels
            for (String hostname : hostnamesForRemoval) {
                removeProxyChannel(hostname, false); // Don't send individual events
            }

            // Add or update channels
            for (GrpcProxyMap mapping : enabledMappings) {
                updateProxyChannel(mapping, false); // Don't send individual events
            }

            // Create a new handler registry
            handlerRegistry = new HostBasedHandlerRegistry(proxyChannels);

            logger.debug("Proxy mapping refresh completed. Active channels: " + proxyChannels.size() +
                    ", hostnames: " + String.join(", ", proxyChannels.keySet()));

            // Publish refresh event to restart the server once
            eventPublisher.publishEvent(ProxyConfigChangedEvent.refreshEvent());
        } catch (Exception e) {
            logger.error("Error refreshing proxy mappings: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Add a new proxy mapping
     *
     * @param mapping The proxy mapping to add
     */
    public synchronized void addProxyMapping(GrpcProxyMap mapping) {
        logger.debug("Adding new proxy mapping: " + mapping.getProxyHostName());
        if ("Y".equals(mapping.getEnable())) {
            updateProxyChannel(mapping, true); // Send event
        }
    }

    /**
     * Update an existing proxy mapping
     *
     * @param mapping The proxy mapping to update
     */
    public synchronized void updateProxyMapping(GrpcProxyMap mapping) {
        logger.debug("Updating proxy mapping: " + mapping.getProxyHostName() + ", enable status: " + mapping.getEnable());
        String proxyHostname = mapping.getProxyHostName();

        if ("Y".equals(mapping.getEnable())) {
            // If enabled, update or add the channel
            updateProxyChannel(mapping, true); // Send event
        } else if ("N".equals(mapping.getEnable()) && proxyChannels.containsKey(proxyHostname)) {
            // If disabled and the channel exists, remove the channel
            removeProxyChannel(proxyHostname, true); // Send event
        }
    }

    /**
     * Batch update the enable status of proxy mappings
     *
     * @param mappings List of proxy mappings to update
     * @param enable Whether to enable (true) or disable (false)
     * @return Number of successfully processed items
     */
    public synchronized int batchUpdateProxyStatus(List<GrpcProxyMap> mappings, boolean enable) {
        if (mappings == null || mappings.isEmpty()) {
            return 0;
        }

        logger.debug("Batch " + (enable ? "enabling" : "disabling") + " proxy services, count: " + mappings.size());

        int successCount = 0;
        boolean needRestart = false;

        // Process each mapping
        for (GrpcProxyMap mapping : mappings) {
            try {
                String proxyHostname = mapping.getProxyHostName();

                if (enable) {
                    // If enabling, update or add the channel
                    updateProxyChannel(mapping, false); // Don't send individual events
                    needRestart = true;
                } else if (!enable && proxyChannels.containsKey(proxyHostname)) {
                    // If disabling and the channel exists, remove the channel
                    removeProxyChannel(proxyHostname, false); // Don't send individual events
                    needRestart = true;
                }

                successCount++;
            } catch (Exception e) {
                logger.error("Error processing proxy mapping: " + mapping.getProxyHostName(), e);
                // Continue processing other mappings
            }
        }

        // If there are any changes, restart is needed
        if (needRestart && successCount > 0) {
            // Update the handler registry
            handlerRegistry = new HostBasedHandlerRegistry(proxyChannels);

            // Publish refresh event to restart the server once
            eventPublisher.publishEvent(ProxyConfigChangedEvent.refreshEvent());
        }

        logger.debug("Batch " + (enable ? "enabling" : "disabling") + " proxy services completed, success: " +
                successCount + ", failure: " + (mappings.size() - successCount));

        return successCount;
    }

    /**
     * Delete a proxy mapping
     *
     * @param mapping The proxy mapping to delete
     */
    public synchronized void deleteProxyMapping(GrpcProxyMap mapping) {
        String proxyHostname = mapping.getProxyHostName();
        logger.debug("Deleting proxy mapping: " + proxyHostname);
        if (proxyChannels.containsKey(proxyHostname)) {
            removeProxyChannel(proxyHostname, true); // Send event
        }
    }

    /**
     * Delete a proxy mapping by hostname
     *
     * @param proxyHostname The proxy hostname to delete
     */
    public synchronized void deleteProxyMappingByHostname(String proxyHostname) {
        logger.debug("Deleting proxy mapping by hostname: " + proxyHostname);
        if (proxyChannels.containsKey(proxyHostname)) {
            removeProxyChannel(proxyHostname, true); // Send event
        }
    }

    /**
     * Detect if the target service supports TLS
     *
     * @param hostname Target hostname
     * @param port Target port
     * @return true if the target service supports TLS, false otherwise
     */
    private boolean detectTlsSupport(String hostname, int port) {
        logger.debug("Detecting if target service supports TLS: " + hostname + ":" + port);

        try {
            // Try to connect to the target service using SSL
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = null;

            try {
                // Set connection timeout
                Socket tempSocket = new Socket();
                tempSocket.connect(new InetSocketAddress(hostname, port), 5000);

                // Start SSL handshake
                socket = (SSLSocket) factory.createSocket(
                        tempSocket, hostname, port, true);

                // Configure SSL parameters to avoid server sending certificate chains
                SSLParameters params = socket.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(params);

                // If auto-trusting all certificates, set handshake timeout shorter
                socket.setSoTimeout(5000);

                // Perform SSL handshake
                socket.startHandshake();

                logger.debug("Target service supports TLS: " + hostname + ":" + port);
                return true;
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Ignore close exceptions
                    }
                }
            }
        } catch (SSLHandshakeException e) {
            // If it's a certificate issue, the server might support TLS, but we don't trust its certificate
            logger.debug("Target service might support TLS, but certificate validation failed (might need to trust certificate): " + hostname + ":" + port);
            return true;
        } catch (Exception e) {
            // Other exceptions, might not support TLS
            logger.debug("Target service might not support TLS: " + hostname + ":" + port + ", error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update or add a proxy channel
     *
     * @param mapping Proxy mapping configuration
     * @param publishEvent Whether to publish an event
     */
    private synchronized void updateProxyChannel(GrpcProxyMap mapping, boolean publishEvent) {
        String proxyHostname = mapping.getProxyHostName();
        boolean isNew = !proxyChannels.containsKey(proxyHostname);

        // Check if a channel with the same settings already exists
        GrpcProxyMap existingMapping = activeProxyMappings.get(proxyHostname);
        if (existingMapping != null &&
                existingMapping.getTargetHostName().equals(mapping.getTargetHostName()) &&
                existingMapping.getTargetPort() == mapping.getTargetPort() &&
                existingMapping.getConnectTimeoutMs() == mapping.getConnectTimeoutMs() &&
                existingMapping.getSendTimeoutMs() == mapping.getSendTimeoutMs() &&
                existingMapping.getReadTimeoutMs() == mapping.getReadTimeoutMs() &&
                Objects.equals(existingMapping.getSecureMode(), mapping.getSecureMode()) &&
                Objects.equals(existingMapping.getTrustedCertsContent(), mapping.getTrustedCertsContent()) &&
                Objects.equals(existingMapping.getAutoTrustUpstreamCerts(), mapping.getAutoTrustUpstreamCerts())) {

            logger.debug("Proxy mapping " + proxyHostname + " configuration unchanged, skipping update");
            return;
        }

        // If there's an existing channel, close it first
        if (proxyChannels.containsKey(proxyHostname)) {
            ManagedChannel oldChannel = proxyChannels.remove(proxyHostname);
            try {
                oldChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                logger.debug("Closed old channel for " + proxyHostname);
            } catch (InterruptedException e) {
                logger.error("Error closing channel for " + proxyHostname, e);
                Thread.currentThread().interrupt();
            }
        }

        // Get secure mode setting
        String secureMode = mapping.getSecureMode();
        if (secureMode == null || secureMode.isEmpty()) {
            secureMode = "AUTO"; // Default to auto detection
        }

        // Determine whether to use TLS
        boolean useTls = false;

        switch (secureMode) {
            case "SECURE":
                useTls = true;
                logger.debug("Proxy mapping " + proxyHostname + " configured to force use of TLS");
                break;
            case "PLAINTEXT":
                useTls = false;
                logger.debug("Proxy mapping " + proxyHostname + " configured to not use TLS (plaintext)");
                break;
            case "AUTO":
            default:
                // Auto-detect if the target service supports TLS
                useTls = detectTlsSupport(mapping.getTargetHostName(), mapping.getTargetPort());
                logger.debug("Proxy mapping " + proxyHostname + " auto-detected TLS result: " +
                        (useTls ? "using TLS" : "using plaintext"));
                break;
        }

        // Create an appropriate channel
        ManagedChannel newChannel;

        if (useTls) {
            try {
                // Create TLS channel
                NettyChannelBuilder channelBuilder = NettyChannelBuilder
                        .forAddress(mapping.getTargetHostName(), mapping.getTargetPort());

                // Set SSL context
                SslContext sslContext;

                // Check whether to auto-trust all upstream certificates or use provided trusted certificates
                if ("Y".equals(mapping.getAutoTrustUpstreamCerts())) {
                    // Use insecure SSL context, auto-trust all certificates
                    sslContext = tlsCertificateManager.createInsecureClientSslContext();
                    logger.debug("Creating TLS channel with auto-trust all certificates for " + proxyHostname);
                } else if (mapping.getTrustedCertsContent() != null && !mapping.getTrustedCertsContent().isEmpty()) {
                    // Use provided trusted certificates
                    sslContext = tlsCertificateManager.createClientSslContext(mapping.getTrustedCertsContent());
                    logger.debug("Creating TLS channel with custom trusted certificates for " + proxyHostname);
                } else {
                    // Use insecure SSL context as no trusted certificates are provided
                    sslContext = tlsCertificateManager.createInsecureClientSslContext();
                    logger.debug("No trusted certificates provided, creating TLS channel with auto-trust all certificates for " + proxyHostname);
                }

                channelBuilder.sslContext(sslContext);

                // Complete channel configuration with optimized HTTP/2 settings
                newChannel = channelBuilder
                        .keepAliveTime(120, TimeUnit.SECONDS)          // Increase to 120 seconds
                        .keepAliveTimeout(30, TimeUnit.SECONDS)        // Increase to 30 seconds
                        .keepAliveWithoutCalls(false)                  // Disable keepalive when no calls
                        .maxInboundMessageSize(20 * 1024 * 1024)       // Increase message size limit
                        .flowControlWindow(2 * 1024 * 1024)            // Increase flow control window size
                        .build();

                logger.debug("Created TLS channel for " + proxyHostname + " with enhanced HTTP/2 settings");

            } catch (Exception e) {
                logger.error("Error creating TLS channel: " + e.getMessage() + ", falling back to plaintext channel", e);

                // If creating TLS channel fails, fall back to plaintext channel
                newChannel = ManagedChannelBuilder
                        .forAddress(mapping.getTargetHostName(), mapping.getTargetPort())
                        .usePlaintext()
                        .keepAliveTime(120, TimeUnit.SECONDS)          // Increase to 120 seconds
                        .keepAliveTimeout(30, TimeUnit.SECONDS)        // Increase to 30 seconds
                        .keepAliveWithoutCalls(false)                  // Disable keepalive when no calls
                        .maxInboundMessageSize(20 * 1024 * 1024)       // Increase message size limit
                        .build();
            }
        } else {
            // Create plaintext channel with optimized HTTP/2 settings
            newChannel = ManagedChannelBuilder
                    .forAddress(mapping.getTargetHostName(), mapping.getTargetPort())
                    .usePlaintext()
                    .keepAliveTime(120, TimeUnit.SECONDS)              // Increase to 120 seconds
                    .keepAliveTimeout(30, TimeUnit.SECONDS)            // Increase to 30 seconds
                    .keepAliveWithoutCalls(false)                      // Disable keepalive when no calls
                    .maxInboundMessageSize(20 * 1024 * 1024)           // Increase message size limit
                    .build();

            logger.debug("Created plaintext channel for " + proxyHostname + " with enhanced HTTP/2 settings");
        }

        proxyChannels.put(proxyHostname, newChannel);
        activeProxyMappings.put(proxyHostname, mapping);

        String securityInfo = useTls ?
                " (using TLS" + ("Y".equals(mapping.getAutoTrustUpstreamCerts()) ? ", auto-trusting all certificates" :
                        (mapping.getTrustedCertsContent() != null ? ", using custom trusted certificates" : ", using system certificates")) + ")" :
                " (plaintext)";

        logger.debug("Added new channel: " + proxyHostname + " -> " +
                mapping.getTargetHostName() + ":" + mapping.getTargetPort() +
                " (timeout settings: connect=" + mapping.getConnectTimeoutMs() +
                "ms, send=" + mapping.getSendTimeoutMs() +
                "ms, read=" + mapping.getReadTimeoutMs() +
                "ms, secure mode: " + secureMode + securityInfo + ")");

        // Update the handler registry
        if (handlerRegistry != null) {
            handlerRegistry = new HostBasedHandlerRegistry(proxyChannels);
        }

        // Publish event to notify configuration has changed
        if (publishEvent) {
            ProxyConfigChangedEvent.ChangeType type = isNew ?
                    ProxyConfigChangedEvent.ChangeType.ADDED :
                    ProxyConfigChangedEvent.ChangeType.UPDATED;
            eventPublisher.publishEvent(new ProxyConfigChangedEvent(type, proxyHostname));
        }
    }

    /**
     * Remove a proxy channel
     *
     * @param proxyHostname The proxy hostname to remove
     * @param publishEvent Whether to publish an event
     */
    private synchronized void removeProxyChannel(String proxyHostname, boolean publishEvent) {
        ManagedChannel channel = proxyChannels.remove(proxyHostname);
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                logger.debug("Removed channel for hostname: " + proxyHostname);
            } catch (InterruptedException e) {
                logger.error("Error closing channel for hostname: " + proxyHostname, e);
                Thread.currentThread().interrupt();
            }

            activeProxyMappings.remove(proxyHostname);

            // Update the handler registry
            if (handlerRegistry != null) {
                handlerRegistry = new HostBasedHandlerRegistry(proxyChannels);
            }

            // Publish event to notify configuration has been removed
            if (publishEvent) {
                eventPublisher.publishEvent(
                        new ProxyConfigChangedEvent(ProxyConfigChangedEvent.ChangeType.REMOVED, proxyHostname));
            }
        }
    }

    /**
     * Get the current handler registry
     *
     * @return The current handler registry
     */
    public HandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Get the list of active proxy hostnames (for diagnostics)
     *
     * @return List of active proxy hostnames
     */
    public List<String> getActiveProxyHostnames() {
        return new ArrayList<>(proxyChannels.keySet());
    }

    /**
     * Get the channel configuration for a specific hostname (for diagnostics)
     *
     * @param proxyHostname The hostname to get configuration for
     * @return The proxy mapping configuration or null if not found
     */
    public GrpcProxyMap getProxyMapping(String proxyHostname) {
        return activeProxyMappings.get(proxyHostname);
    }
}