import io.github.alvinchiu.gstreamgate.marshaller.PassthroughMarshaller;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PassthroughMarshallerTest {

    private final PassthroughMarshaller marshaller = new PassthroughMarshaller();

    @Test
    void parse_withValidStream_returnsIdenticalData() throws Exception {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(data);
        InputStream result = marshaller.parse(input);
        assertArrayEquals(data, result.readAllBytes());
    }

    @Test
    void stream_withValidStream_returnsIdenticalData() throws Exception {
        byte[] data = "world".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(data);
        InputStream result = marshaller.stream(input);
        assertArrayEquals(data, result.readAllBytes());
    }

    @Test
    void parse_null_returnsEmptyStream() throws Exception {
        InputStream result = marshaller.parse(null);
        assertNotNull(result);
        assertEquals(0, result.available());
    }

    @Test
    void stream_null_returnsEmptyStream() throws Exception {
        InputStream result = marshaller.stream(null);
        assertNotNull(result);
        assertEquals(0, result.available());
    }

    @Test
    void parse_doesNotReadInputStream() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        assertSame(failing, marshaller.parse(failing));
    }

    @Test
    void stream_doesNotReadInputStream() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("fail");
            }
        };
        assertSame(failing, marshaller.stream(failing));
    }
}

