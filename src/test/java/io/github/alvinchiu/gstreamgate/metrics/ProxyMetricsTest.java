package io.github.alvinchiu.gstreamgate.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProxyMetricsTest {
    private ProxyMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ProxyMetrics(new SimpleMeterRegistry());
        metrics.reset();
    }

    @Test
    void recordRequestUpdatesCounters() {
        metrics.recordRequest("testMethod", "host1", Duration.ofMillis(50), true, 100);
        assertEquals(1, metrics.getMeterRegistry().get("grpc.proxy.request.duration").timer().count());
        assertEquals(1.0, metrics.getMeterRegistry().get("grpc.proxy.requests.success").counter().count());
        assertEquals(0.0, metrics.getMeterRegistry().get("grpc.proxy.requests.error").counter().count());
        assertEquals(100, metrics.getTotalBytesTransferred());
    }

    @Test
    void connectionCountersReflectActiveConnections() {
        metrics.recordConnection("host1", true);
        metrics.recordConnection("host1", true);
        metrics.recordConnection("host1", false);
        assertEquals(1, metrics.getActiveConnectionsForTarget("host1"));
        assertEquals(1, metrics.getTotalActiveConnections());
    }
}
