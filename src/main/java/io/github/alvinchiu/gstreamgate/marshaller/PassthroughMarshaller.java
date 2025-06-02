package io.github.alvinchiu.gstreamgate.marshaller;

import com.google.common.io.ByteStreams;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A marshaller implementation that passes through binary data without any
 * actual serialization or deserialization. This allows proxying gRPC calls
 * without knowledge of the message types.
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
            byte[] bytes = ByteStreams.toByteArray(stream);
            logger.debug("Parsed input stream, size: " + bytes.length + " bytes");
            return new ByteArrayInputStream(bytes);
        } catch (IOException e) {
            logger.error("IO error parsing input stream: " + e.getMessage());
            throw new RuntimeException(
                    new StatusException(Status.INTERNAL
                            .withDescription("IO error parsing input stream: " + e.getMessage())
                            .withCause(e))
            );
        } catch (Exception e) {
            logger.error("Unexpected error parsing input stream: " + e.getMessage());
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
            byte[] bytes = ByteStreams.toByteArray(value);
            logger.debug("Streamed input stream, size: " + bytes.length + " bytes");
            return new ByteArrayInputStream(bytes);
        } catch (IOException e) {
            logger.error("IO error streaming input stream: " + e.getMessage());
            throw new RuntimeException(
                    new StatusException(Status.INTERNAL
                            .withDescription("IO error streaming input stream: " + e.getMessage())
                            .withCause(e))
            );
        } catch (Exception e) {
            logger.error("Unexpected error streaming input stream: " + e.getMessage());
            throw new RuntimeException(
                    new StatusException(Status.UNKNOWN
                            .withDescription("Unexpected error streaming input stream: " + e.getMessage())
                            .withCause(e))
            );
        }
    }
}