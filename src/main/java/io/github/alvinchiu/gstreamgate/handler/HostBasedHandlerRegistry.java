package io.github.alvinchiu.gstreamgate.handler;

import io.grpc.HandlerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.alvinchiu.gstreamgate.marshaller.PassthroughMarshaller;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A handler registry that routes requests based on hostname.
 * This allows the proxy to forward requests to different target services
 * based on the hostname in the authority header.
 */
public class HostBasedHandlerRegistry extends HandlerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(HostBasedHandlerRegistry.class);
    private final Map<String, ManagedChannel> proxyChannels;

    /**
     * Creates a new host-based handler registry with the given proxy channels.
     *
     * @param proxyChannels Map of hostname to channel for each proxy mapping
     */
    public HostBasedHandlerRegistry(Map<String, ManagedChannel> proxyChannels) {
        // Create a new HashMap and copy all entries to ensure it won't be affected by external map changes
        this.proxyChannels = new HashMap<>(proxyChannels);

        // Add diagnostic logs
        if (!this.proxyChannels.isEmpty()) {
            logger.debug("Initializing HostBasedHandlerRegistry, including the following hostnames: " +
                    String.join(", ", this.proxyChannels.keySet()));
        } else {
            logger.warn("Initializing HostBasedHandlerRegistry, but no hostnames are included");
        }
    }

    @Override
    public ServerMethodDefinition<InputStream, InputStream> lookupMethod(
            String methodName, String authority) {
        // Extract hostname from authority header, with null check
        if (authority == null) {
            logger.warn("Received null authority header in lookupMethod, method: " + methodName);
            return null;
        }

        String hostname;
        try {
            hostname = authority.split(":")[0];
        } catch (Exception e) {
            logger.warn("Failed to parse authority header: " + authority + ", method: " + methodName);
            return null;
        }

        // Check if hostname is empty
        if (hostname.isEmpty()) {
            logger.warn("Empty hostname extracted from authority: " + authority + ", method: " + methodName);
            return null;
        }

        logger.debug("Looking up handler for hostname '" + hostname + "', method: " + methodName);
        logger.debug("Available hostname mappings: " + String.join(", ", proxyChannels.keySet()));

        // Look up the channel for this hostname
        ManagedChannel channel = proxyChannels.get(hostname);

        // If no mapping exists for this hostname, log a warning and return null
        if (channel == null) {
            logger.warn("No mapping found for hostname: " + hostname);
            return null;
        }

        // Create and return a proxy method definition
        logger.debug("Creating proxy method definition for hostname '" + hostname + "', method: " + methodName);
        return createProxyMethodDefinition(methodName, channel);
    }

    /**
     * Creates a proxy method definition for the given method and channel.
     *
     * @param methodName The full method name
     * @param channel The channel to proxy to
     * @return A server method definition that proxies to the given channel
     */
    private ServerMethodDefinition<InputStream, InputStream> createProxyMethodDefinition(
            String methodName, ManagedChannel channel) {
        return ServerMethodDefinition.create(
                MethodDescriptor.<InputStream, InputStream>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNKNOWN)
                        .setFullMethodName(methodName)
                        .setRequestMarshaller(new PassthroughMarshaller())
                        .setResponseMarshaller(new PassthroughMarshaller())
                        .build(),
                new ProxyServerCallHandler(channel, methodName));
    }

    /**
     * Get all proxy hostnames (for diagnostics)
     *
     * @return Set of proxy hostnames
     */
    public Set<String> getHostnames() {
        return new HashSet<>(proxyChannels.keySet());
    }
}