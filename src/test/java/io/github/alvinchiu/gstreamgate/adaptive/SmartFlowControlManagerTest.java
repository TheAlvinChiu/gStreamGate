package io.github.alvinchiu.gstreamgate.adaptive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SmartFlowControlManager} verifying adaptive behaviour of
 * request counts.
 */
public class SmartFlowControlManagerTest {

    @Test
    void testCalculateRequestCountAndCleanup() {
        SmartFlowControlManager manager = new SmartFlowControlManager();
        String callId = "flow-test";

        // setup flow control and mark call as streaming
        manager.initializeFlowControl(callId);
        assertEquals(2, manager.getInitialRequestCount(callId, true));

        // no pending messages -> request count should increase from 1 to 2
        int first = manager.calculateRequestCount(callId, true);
        assertEquals(2, first);

        // still no pending -> increase again to 4
        int second = manager.calculateRequestCount(callId, true);
        assertEquals(4, second);

        // pending equal to current results in stable request counts
        for (int i = 0; i < 4; i++) {
            manager.startProcessingMessage(callId);
        }
        int stable1 = manager.calculateRequestCount(callId, true);
        assertEquals(1, stable1); // current count maintained
        int stable2 = manager.calculateRequestCount(callId, true);
        assertEquals(stable1, stable2);

        // pending greater than current leads to a decrease
        manager.startProcessingMessage(callId); // now pending = 5 (> current=4)
        int decreased = manager.calculateRequestCount(callId, true);
        assertEquals(1, decreased); // current request count shrinks internally

        // after clearing pending, request count rises from reduced state
        for (int i = 0; i < 5; i++) {
            manager.completeProcessingMessage(callId);
        }
        int afterClear = manager.calculateRequestCount(callId, true);
        assertEquals(4, afterClear); // doubled from reduced count 2

        // cleanup should remove all state
        manager.cleanupFlowControl(callId);
        assertEquals(2, manager.calculateRequestCount(callId, true));
        assertFalse(manager.isShowingStreamingBehavior(callId));
    }
}
