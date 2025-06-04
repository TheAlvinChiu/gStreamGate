package io.github.alvinchiu.gstreamgate.marshaller;

import io.grpc.MethodDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            return InputStream.nullInputStream();
        }

        logger.debug("Passing through input stream without copying");
        return stream;
    }

    @Override
    public InputStream stream(InputStream value) {
        if (value == null) {
            logger.warn("Received null input stream for streaming");
            return InputStream.nullInputStream();
        }

        logger.debug("Streaming input stream without copying");
        return value;
    }
}
