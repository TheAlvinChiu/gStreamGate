import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager.CircuitBreaker;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager.CircuitBreakerOpenException;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager.CircuitBreakerStatus;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerManagerTest {
    private CircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        manager = new CircuitBreakerManager();
    }

    @Test
    void circuitOpensAfterFailures() {
        CircuitBreaker breaker = manager.getCircuitBreaker("serviceA");
        @SuppressWarnings("unchecked")
        Supplier<String> fail = Mockito.mock(Supplier.class);
        Mockito.when(fail.get()).thenThrow(new RuntimeException("fail"));

        for (int i = 0; i < 10; i++) {
            assertThrows(RuntimeException.class, () -> breaker.execute(fail));
        }

        assertEquals(State.OPEN, breaker.getStatus().getState());
        assertThrows(CircuitBreakerOpenException.class, () -> breaker.execute(fail));
    }

    @Test
    void halfOpenSuccessClosesCircuit() throws Exception {
        CircuitBreaker breaker = manager.getCircuitBreaker("serviceB");
        @SuppressWarnings("unchecked")
        Supplier<String> fail = Mockito.mock(Supplier.class);
        Mockito.when(fail.get()).thenThrow(new RuntimeException("fail"));

        for (int i = 0; i < 10; i++) {
            try {
                breaker.execute(fail);
            } catch (Exception ignored) {
            }
        }
        assertEquals(State.OPEN, breaker.getStatus().getState());

        Field stField = CircuitBreaker.class.getDeclaredField("stateTransitionTime");
        stField.setAccessible(true);
        AtomicLong st = (AtomicLong) stField.get(breaker);
        st.set(System.currentTimeMillis() - Duration.ofSeconds(61).toMillis());

        @SuppressWarnings("unchecked")
        Supplier<String> success = Mockito.mock(Supplier.class);
        Mockito.when(success.get()).thenReturn("ok");

        breaker.execute(success);

        assertEquals(State.CLOSED, breaker.getStatus().getState());
    }

    @Test
    void slidingWindowStatistics() {
        CircuitBreaker breaker = manager.getCircuitBreaker("serviceC");
        @SuppressWarnings("unchecked")
        Supplier<String> success = Mockito.mock(Supplier.class);
        Mockito.when(success.get()).thenReturn("ok");
        @SuppressWarnings("unchecked")
        Supplier<String> fail = Mockito.mock(Supplier.class);
        Mockito.when(fail.get()).thenThrow(new RuntimeException("fail"));

        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> breaker.execute(success));
        }
        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class, () -> breaker.execute(fail));
        }

        CircuitBreakerStatus status = breaker.getStatus();
        assertEquals(5, status.getTotalRequests());
        assertEquals(0.4, status.getFailureRate(), 0.0001);
    }
}
