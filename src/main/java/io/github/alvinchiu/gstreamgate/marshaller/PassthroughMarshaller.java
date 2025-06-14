package io.github.alvinchiu.gstreamgate.marshaller;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A marshaller implementation that passes through binary data without any
 * actual serialization or deserialization. This allows proxying gRPC calls
 * without knowledge of the message types.
 * 
 * This implementation avoids blocking I/O operations and directly returns
 * the input stream to maintain true zero-copy behavior when possible.
 */
public class PassthroughMarshaller implements MethodDescriptor.Marshaller<InputStream> {
    private static final Logger logger = LoggerFactory.getLogger(PassthroughMarshaller.class);

    @Override
    public InputStream parse(InputStream stream) {
        if (stream == null) {
            logger.warn("Received null input stream for parsing");
            return new ByteArrayInputStream(new byte[0]);
        }

        try {
            // Return the stream directly to avoid blocking I/O and memory copying
            // This maintains zero-copy behavior and prevents performance bottlenecks
            logger.debug("Parsing input stream (zero-copy pass-through)");
            return stream;
        } catch (Exception e) {
            logger.error("Unexpected error parsing input stream: {}", e.getMessage(), e);
            throw new RuntimeException(
                    new StatusException(Status.UNKNOWN
                            .withDescription("Unexpected error parsing input stream: " + e.getMessage())
                            .withCause(e))
            );
        }
    }

    @Override
    public InputStream stream(InputStream value) {
        if (value == null) {
            logger.warn("Received null input stream for streaming");
            return new ByteArrayInputStream(new byte[0]);
        }

        try {
            // Return the stream directly to avoid blocking I/O and memory copying
            // This maintains zero-copy behavior and prevents performance bottlenecks
            logger.debug("Streaming input stream (zero-copy pass-through)");
            return value;
        } catch (Exception e) {
            logger.error("Unexpected error streaming input stream: {}", e.getMessage(), e);
            throw new RuntimeException(
                    new StatusException(Status.UNKNOWN
                            .withDescription("Unexpected error streaming input stream: " + e.getMessage())
                            .withCause(e))
            );
        }
    }
}