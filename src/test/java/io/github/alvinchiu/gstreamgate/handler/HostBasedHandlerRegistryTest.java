package io.github.alvinchiu.gstreamgate.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerMethodDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HostBasedHandlerRegistryTest {
    private static ManagedChannel channel;

    @BeforeAll
    static void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 65535).usePlaintext().build();
    }

    @AfterAll
    static void shutdown() {
        channel.shutdownNow();
    }

    @Test
    void lookupReturnsDefinitionWhenHostnameMatches() {
        Map<String, ManagedChannel> map = new HashMap<>();
        map.put("example.com", channel);
        HostBasedHandlerRegistry registry = new HostBasedHandlerRegistry(map);

        ServerMethodDefinition<InputStream, InputStream> def = registry.lookupMethod("service/method", "example.com:1234");
        assertNotNull(def);
        assertEquals("service/method", def.getMethodDescriptor().getFullMethodName());
    }

    @Test
    void lookupReturnsNullForUnknownHost() {
        Map<String, ManagedChannel> map = new HashMap<>();
        map.put("example.com", channel);
        HostBasedHandlerRegistry registry = new HostBasedHandlerRegistry(map);

        assertNull(registry.lookupMethod("service/method", "unknown.com:1234"));
        assertNull(registry.lookupMethod("service/method", null));
    }
}
