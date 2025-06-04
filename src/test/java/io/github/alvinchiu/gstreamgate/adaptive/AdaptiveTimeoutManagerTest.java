package io.github.alvinchiu.gstreamgate.adaptive;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveTimeoutManagerTest {

    private static final int MIN_TIMEOUT = 60;
    private static final int MAX_TIMEOUT = 600;

    @Test
    void unaryTimeoutIncreasesWithinBounds() throws Exception {
        AdaptiveTimeoutManager manager = new AdaptiveTimeoutManager();
        String method = "unaryMethod";

        for (int i = 0; i < 6; i++) {
            String callId = "u" + i;
            manager.startCall(method, callId);
            manager.recordMessage(callId); // single message
            manager.completeCall(method, callId);
        }

        assertEquals(MIN_TIMEOUT, manager.getTimeout(method));

        Field methodStatsField = AdaptiveTimeoutManager.class.getDeclaredField("methodStats");
        methodStatsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) methodStatsField.get(manager);
        Object stats = map.get(method);

        Field maxDurationField = stats.getClass().getDeclaredField("maxDuration");
        maxDurationField.setAccessible(true);
        ((AtomicLong) maxDurationField.get(stats)).set(120_000L); // 120s

        Field totalDurationField = stats.getClass().getDeclaredField("totalDuration");
        totalDurationField.setAccessible(true);
        ((AtomicLong) totalDurationField.get(stats)).set(120_000L * 6);

        Class<?> msClass = Class.forName("io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager$MethodStats");
        Method update = AdaptiveTimeoutManager.class.getDeclaredMethod("updateTimeoutForMethod", String.class, msClass);
        update.setAccessible(true);
        update.invoke(manager, method, stats);

        int updated = manager.getTimeout(method);
        assertTrue(updated > MIN_TIMEOUT && updated <= 300);
    }

    @Test
    void streamingTimeoutCappedAndDetected() throws Exception {
        AdaptiveTimeoutManager manager = new AdaptiveTimeoutManager();
        String method = "streamMethod";

        for (int i = 0; i < 6; i++) {
            String callId = "s" + i;
            manager.startCall(method, callId);
            manager.recordMessage(callId);
            manager.recordMessage(callId); // >1 messages triggers streaming
            manager.completeCall(method, callId);
        }

        Field methodStatsField = AdaptiveTimeoutManager.class.getDeclaredField("methodStats");
        methodStatsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) methodStatsField.get(manager);
        Object stats = map.get(method);

        Field streamingField = stats.getClass().getDeclaredField("streamingBehaviorObserved");
        streamingField.setAccessible(true);
        boolean detected = ((AtomicBoolean) streamingField.get(stats)).get();
        assertTrue(detected);

        Field maxDurationField = stats.getClass().getDeclaredField("maxDuration");
        maxDurationField.setAccessible(true);
        ((AtomicLong) maxDurationField.get(stats)).set(400_000L); // 400s

        Field totalDurationField = stats.getClass().getDeclaredField("totalDuration");
        totalDurationField.setAccessible(true);
        ((AtomicLong) totalDurationField.get(stats)).set(400_000L * 6);

        Class<?> msClass = Class.forName("io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager$MethodStats");
        Method update = AdaptiveTimeoutManager.class.getDeclaredMethod("updateTimeoutForMethod", String.class, msClass);
        update.setAccessible(true);
        update.invoke(manager, method, stats);

        int updated = manager.getTimeout(method);
        assertEquals(MAX_TIMEOUT, updated);
    }
}
