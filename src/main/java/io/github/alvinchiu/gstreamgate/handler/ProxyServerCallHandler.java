package io.github.alvinchiu.gstreamgate.handler;

import com.google.common.io.ByteStreams;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import io.github.alvinchiu.gstreamgate.adaptive.SmartFlowControlManager;
import io.github.alvinchiu.gstreamgate.marshaller.PassthroughMarshaller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A proxy server call handler for gRPC services.
 * Responsible for forwarding requests to the target service and responses back to the client.
 * Features adaptive timeout and smart flow control capabilities to automatically adapt to different RPC types.
 */
public class ProxyServerCallHandler implements ServerCallHandler<InputStream, InputStream> {
    private static final Logger logger = LoggerFactory.getLogger(ProxyServerCallHandler.class);
    private final ManagedChannel channel;
    private final String fullMethodName;
    private static final AtomicLong callCounter = new AtomicLong(0);

    // Resource monitoring counters
    private static final AtomicInteger activeClientStreams = new AtomicInteger(0);
    private static final AtomicInteger totalMessagesReceived = new AtomicInteger(0);
    private static final AtomicInteger activeCalls = new AtomicInteger(0);

    // Periodic logging
    static {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            logger.info("Resource usage metrics: active calls=" + activeCalls.get() +
                    ", active client streams=" + activeClientStreams.get() +
                    ", total messages=" + totalMessagesReceived.get());
        }, 30, 30, TimeUnit.SECONDS);
    }

    // Injected manager instances
    private static AdaptiveTimeoutManager timeoutManager;
    private static SmartFlowControlManager flowControlManager;

    // Conservative default timeout settings (seconds)
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // Conservative 5-minute default timeout

    // Timeout settings for different RPC types
    private static final int MIN_TIMEOUT_SECONDS = 60; // Minimum timeout (1 minute)

    /**
     * Static method to inject manager instances
     */
    public static void injectManagers(AdaptiveTimeoutManager timeoutMgr, SmartFlowControlManager flowControlMgr) {
        timeoutManager = timeoutMgr;
        flowControlManager = flowControlMgr;
    }

    /**
     * Constructor, creates a new proxy handler
     *
     * @param channel The channel to the target service
     * @param fullMethodName The full method name being proxied
     */
    public ProxyServerCallHandler(ManagedChannel channel, String fullMethodName) {
        this.channel = channel;
        this.fullMethodName = fullMethodName;
        logger.debug("Created ProxyServerCallHandler for method: " + fullMethodName);
    }

    /**
     * Generate a unique call ID for logging
     */
    private String generateCallId() {
        String methodShortName = fullMethodName;
        int lastSlashIndex = fullMethodName.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < fullMethodName.length() - 1) {
            methodShortName = fullMethodName.substring(lastSlashIndex + 1);
        }

        return methodShortName + "-" +
                callCounter.incrementAndGet() + "-" +
                java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Called by gRPC when a new call is received.
     * Sets up proxying between the incoming server call and a new client call.
     */
    @Override
    public ServerCall.Listener<InputStream> startCall(
            ServerCall<InputStream, InputStream> serverCall,
            Metadata headers) {

        // Increment active call count
        activeCalls.incrementAndGet();

        // Generate unique call ID for tracking
        String callId = generateCallId();

        logger.debug("[" + callId + "] Starting call for method: " + fullMethodName);
        logger.debug("[" + callId + "] Headers received: " + headers);

        try {
            // Check if managers are injected
            if (timeoutManager == null || flowControlManager == null) {
                logger.warn("[" + callId + "] Adaptive managers not injected, using default settings");
                return fallbackStartCall(serverCall, headers, callId);
            }

            // Start tracking call for adaptive mechanisms
            timeoutManager.startCall(fullMethodName, callId);
            flowControlManager.initializeFlowControl(callId);

            // Get adaptive timeout for the method (based on historical call patterns)
            int timeoutSeconds = getTimeoutForMethod(fullMethodName, callId);

            // Set deadline for the request
            long deadlineMs = System.currentTimeMillis() + (timeoutSeconds * 1000);
            logger.debug("[" + callId + "] Setting deadline to " + timeoutSeconds + " seconds from now");

            // Create client call to target service, with increased message size limits
            ClientCall<InputStream, InputStream> clientCall = channel.newCall(
                    MethodDescriptor.<InputStream, InputStream>newBuilder()
                            .setType(MethodDescriptor.MethodType.UNKNOWN)  // We use our own type detection
                            .setFullMethodName(fullMethodName)
                            .setRequestMarshaller(new PassthroughMarshaller())
                            .setResponseMarshaller(new PassthroughMarshaller())
                            .build(),
                    CallOptions.DEFAULT
                            .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)
                            .withMaxInboundMessageSize(20 * 1024 * 1024)  // Increase message size limit
                            .withMaxOutboundMessageSize(20 * 1024 * 1024));  // Increase outbound message size limit

            // Determine if likely streaming RPC (based on method name or previous call patterns)
            boolean isLikelyStreaming = isLikelyStreamingMethod(fullMethodName) ||
                    flowControlManager.isShowingStreamingBehavior(callId);

            // Create adaptive listener for client call
            AdaptiveClientCallListener clientCallListener =
                    new AdaptiveClientCallListener(clientCall, serverCall, callId, isLikelyStreaming);

            // Start client call with original headers
            logger.debug("[" + callId + "] Starting client call");
            clientCall.start(clientCallListener, headers);

            // Set initial flow control
            int initialClientRequest = flowControlManager.getInitialRequestCount(callId, isLikelyStreaming);
            int initialServerRequest = flowControlManager.getInitialRequestCount(callId, isLikelyStreaming);

            logger.debug("[" + callId + "] Setting initial flow control (likely streaming: " + isLikelyStreaming +
                    ", client req: " + initialClientRequest + ", server req: " + initialServerRequest + ")");

            clientCall.request(initialClientRequest);

            // Create and return adaptive listener for server call
            logger.debug("[" + callId + "] Creating adaptive server call listener");
            AdaptiveServerCallListener listener = new AdaptiveServerCallListener(
                    clientCall, serverCall, callId, isLikelyStreaming);

            // For client streaming or bidirectional methods, specifically increase request count
            if (fullMethodName.contains("ClientStream") || fullMethodName.contains("Bidirectional")) {
                // Request more messages (enough to handle multiple messages)
                int highInitialRequest = 20; // Enough to handle many messages
                logger.debug("[" + callId + "] Detected streaming method, requesting " +
                        highInitialRequest + " messages initially");
                serverCall.request(highInitialRequest);
            } else {
                serverCall.request(initialServerRequest);
            }

            return listener;
        } catch (Exception e) {
            logger.error("[" + callId + "] Error in startCall for method: " + fullMethodName + ": " + e.getMessage());
            // Decrease count since call failed
            activeCalls.decrementAndGet();
            throw e;
        }
    }

    /**
     * Fallback method when managers are not injected
     */
    private ServerCall.Listener<InputStream> fallbackStartCall(
            ServerCall<InputStream, InputStream> serverCall,
            Metadata headers, String callId) {

        // Use conservative timeout for all methods
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

        // Create client call to target service, with increased message size limits
        ClientCall<InputStream, InputStream> clientCall = channel.newCall(
                MethodDescriptor.<InputStream, InputStream>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNKNOWN)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(new PassthroughMarshaller())
                        .setResponseMarshaller(new PassthroughMarshaller())
                        .build(),
                CallOptions.DEFAULT
                        .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)
                        .withMaxInboundMessageSize(20 * 1024 * 1024)
                        .withMaxOutboundMessageSize(20 * 1024 * 1024));

        // Create standard listener
        ClientCall.Listener<InputStream> clientCallListener = new ClientCall.Listener<InputStream>() {
            private boolean headersSent = false;

            @Override
            public void onMessage(InputStream message) {
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }
                serverCall.sendMessage(message);
                serverCall.request(1);
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }
                serverCall.close(status, trailers);
                // Decrease count when call completes
                activeCalls.decrementAndGet();
            }

            @Override
            public void onReady() {
                // No-op
            }
        };

        // Start client call
        clientCall.start(clientCallListener, headers);

        // Set higher initial request count for streaming RPCs
        boolean isLikelyStreaming = isLikelyStreamingMethod(fullMethodName);
        int initialRequestCount = isLikelyStreaming ? 2 : 1;

        // Set initial flow control
        clientCall.request(initialRequestCount);

        // For client streaming methods, increase request count
        if (fullMethodName.contains("ClientStream") || fullMethodName.contains("Bidirectional")) {
            initialRequestCount = 10; // Significantly increase for client streams
        }

        serverCall.request(initialRequestCount);

        // Track active streams
        final Set<InputStream> activeStreams = Collections.newSetFromMap(new ConcurrentHashMap<>());
        // Track half-closed state
        final AtomicBoolean halfCloseProcessed = new AtomicBoolean(false);

        // Create server listener
        return new ServerCall.Listener<InputStream>() {
            private int messageCount = 0;
            private boolean isActive = true;
            private final List<byte[]> bufferedMessages = new ArrayList<>();

            @Override
            public void onMessage(InputStream message) {
                if (!isActive) {
                    try {
                        message.close();
                    } catch (IOException e) {
                        logger.warn("[" + callId + "] Error closing message in inactive state: " + e.getMessage());
                    }
                    return;
                }

                messageCount++;
                activeStreams.add(message);
                totalMessagesReceived.incrementAndGet();

                try {
                    // Read message content
                    byte[] messageData = ByteStreams.toByteArray(message);

                    // Add to buffer
                    bufferedMessages.add(messageData);
                    logger.debug("[" + callId + "] Message #" + messageCount + " added to buffer");

                    // Process buffered messages
                    processBufferedMessages();

                    // Remove from tracking list
                    activeStreams.remove(message);

                    // Request more messages
                    serverCall.request(1);
                } catch (Exception e) {
                    logger.error("[" + callId + "] Error forwarding message: " + e.getMessage());
                    try {
                        message.close();
                    } catch (IOException ioe) {
                        // Ignore close exceptions
                    }
                    activeStreams.remove(message);
                    isActive = false;

                    // Close call
                    Status status = Status.INTERNAL.withDescription("Error forwarding message: " + e.getMessage());
                    serverCall.close(status, new Metadata());
                    clientCall.cancel("Error processing message", null);

                    // Cleanup resources
                    cleanupStreams();

                    // Decrease count
                    activeCalls.decrementAndGet();
                }
            }

            private void processBufferedMessages() {
                while (!bufferedMessages.isEmpty() && isActive) {
                    byte[] data = bufferedMessages.get(0);
                    try {
                        // Create new input stream
                        InputStream newStream = new ByteArrayInputStream(data);

                        // Send to upstream service
                        clientCall.sendMessage(newStream);
                        logger.debug("[" + callId + "] Sent buffered message to upstream service");

                        // Remove from buffer
                        bufferedMessages.remove(0);

                        // Request next message
                        clientCall.request(1);
                    } catch (Exception e) {
                        logger.error("[" + callId + "] Error sending buffered message: " + e.getMessage());
                        break;
                    }
                }

                // If buffer is empty and half-close has been marked, perform half-close
                if (bufferedMessages.isEmpty() && halfCloseProcessed.get() && isActive) {
                    try {
                        clientCall.halfClose();
                        logger.debug("[" + callId + "] Sending delayed half-close");
                    } catch (IllegalStateException e) {
                        if (e.getMessage().contains("already half-closed")) {
                            logger.debug("[" + callId + "] Call already half-closed, ignored");
                        } else {
                            logger.error("[" + callId + "] Error in delayed half-close: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        logger.error("[" + callId + "] Error in delayed half-close: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onHalfClose() {
                if (!isActive) return;

                try {
                    // Check if half-close already processed
                    if (halfCloseProcessed.compareAndSet(false, true)) {
                        logger.debug("[" + callId + "] Client stream completed, beginning half-close operation");

                        // Remove blocking Thread.sleep() - process buffered messages directly
                        // The message buffering system will handle proper sequencing

                        // Ensure all buffered messages are processed
                        processBufferedMessages();

                        // Clean up any unused streams
                        cleanupStreams();

                        // If buffer is empty, perform half-close
                        if (bufferedMessages.isEmpty() && isActive) {
                            try {
                                clientCall.halfClose();
                                logger.debug("[" + callId + "] Client call half-closed successfully");
                            } catch (IllegalStateException e) {
                                if (e.getMessage().contains("already half-closed")) {
                                    logger.debug("[" + callId + "] Call already half-closed, ignored");
                                } else {
                                    throw e;
                                }
                            }
                        } else {
                            logger.debug("[" + callId + "] Delaying half-close until buffer is processed");
                        }
                    } else {
                        logger.debug("[" + callId + "] Half-close already processed, ignoring duplicate call");
                    }
                } catch (Exception e) {
                    logger.error("[" + callId + "] Error during half-close: " + e.getMessage());
                    isActive = false;

                    // Close call
                    Status status = Status.INTERNAL.withDescription("Error during half-close: " + e.getMessage());
                    serverCall.close(status, new Metadata());
                    clientCall.cancel("Error during half-close", null);

                    // Cleanup resources
                    cleanupStreams();

                    // Decrease count
                    activeCalls.decrementAndGet();
                }
            }

            @Override
            public void onCancel() {
                try {
                    clientCall.cancel("Client cancelled the call", null);
                } catch (Exception e) {
                    logger.error("[" + callId + "] Error cancelling client call: " + e.getMessage());
                } finally {
                    isActive = false;

                    // Cleanup resources
                    cleanupStreams();

                    // Decrease count
                    activeCalls.decrementAndGet();
                }
            }

            @Override
            public void onComplete() {
                isActive = false;
                cleanupStreams();
                activeCalls.decrementAndGet();
            }

            @Override
            public void onReady() {
                // No-op
            }

            private void cleanupStreams() {
                for (InputStream stream : activeStreams) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // Ignore close exceptions
                    }
                }
                activeStreams.clear();
            }
        };
    }

    /**
     * Determine if a method is likely streaming based on its name
     */
    private boolean isLikelyStreamingMethod(String methodName) {
        String lowerName = methodName.toLowerCase();
        return lowerName.contains("stream") ||
                lowerName.contains("watch") ||
                lowerName.contains("observe") ||
                lowerName.contains("monitor") ||
                lowerName.contains("subscribe") ||
                lowerName.contains("list") ||
                lowerName.contains("chat") ||
                lowerName.contains("continuous");
    }

    /**
     * Get suggested timeout for a method
     */
    private int getTimeoutForMethod(String methodName, String callId) {
        // First try to get timeout from adaptive manager
        int adaptiveTimeout = timeoutManager.getTimeout(methodName);

        if (adaptiveTimeout > 0) {
            // Use adaptive timeout, but ensure at least minimum timeout
            return Math.max(adaptiveTimeout, MIN_TIMEOUT_SECONDS);
        }

        // If adaptive manager has no suggestion, base on method name
        if (isLikelyStreamingMethod(methodName)) {
            logger.debug("[" + callId + "] Method name suggests streaming, using extended timeout");
            return DEFAULT_TIMEOUT_SECONDS;
        }

        // Otherwise use default timeout (conservative setting)
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Adaptive client call listener, handles responses from target service
     */
    private class AdaptiveClientCallListener extends ClientCall.Listener<InputStream> {
        private final ClientCall<InputStream, InputStream> clientCall;
        private final ServerCall<InputStream, InputStream> serverCall;
        private final String callId;
        private boolean headersSent = false;
        private int messageCount = 0;
        private boolean isStreaming = false;

        // Resource tracking
        private final Set<InputStream> activeStreams = Collections.newSetFromMap(new ConcurrentHashMap<>());

        /**
         * Creates a new adaptive client call listener
         *
         * @param serverCall Server call to send responses back to original client
         * @param callId Call ID for logging
         */
        public AdaptiveClientCallListener(ClientCall<InputStream, InputStream> clientCall,
                                          ServerCall<InputStream, InputStream> serverCall,
                                          String callId, boolean initialIsStreaming) {
            this.clientCall = clientCall;
            this.serverCall = serverCall;
            this.callId = callId;
            this.isStreaming = initialIsStreaming;
            logger.debug("[" + callId + "] Created new AdaptiveClientCallListener");
        }

        /**
         * Called when a message is received from the target service.
         * Forwards the message to the original client and updates flow control.
         */
        @Override
        public void onMessage(InputStream message) {
            messageCount++;
            activeStreams.add(message);

            logger.debug("[" + callId + "] AdaptiveClientCallListener received message #" + messageCount);

            // Update streaming RPC detection
            if (messageCount > 1) {
                isStreaming = true;
            }

            // Record message reception (for statistics)
            if (timeoutManager != null) {
                timeoutManager.recordMessage(callId);
            }
            if (flowControlManager != null) {
                flowControlManager.startProcessingMessage(callId);
            }

            try {
                // Ensure headers are sent before any messages
                if (!headersSent) {
                    logger.debug("[" + callId + "] Sending initial headers");
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // Forward message to original client
                serverCall.sendMessage(message);
                logger.debug("[" + callId + "] Message #" + messageCount + " forwarded to server call");

                // Complete message processing
                if (flowControlManager != null) {
                    flowControlManager.completeProcessingMessage(callId);
                }

                // Remove stream tracking
                activeStreams.remove(message);

                // Apply adaptive flow control - request more messages
                if (flowControlManager != null) {
                    flowControlManager.applyFlowControl(serverCall, callId, isStreaming);
                } else {
                    // Default request strategy
                    serverCall.request(1);
                }
            } catch (Exception e) {
                logger.error("[" + callId + "] Error forwarding message to server call: " + e.getMessage());

                // Cleanup resources
                try {
                    message.close();
                } catch (IOException ioe) {
                    // Ignore close exceptions
                }
                activeStreams.remove(message);

                if (flowControlManager != null) {
                    flowControlManager.completeProcessingMessage(callId);
                }

                throw e;
            }
        }

        /**
         * Called when the call to the target service is closed.
         * Closes the call to the original client with the same status and trailers.
         */
        @Override
        public void onClose(Status status, Metadata trailers) {
            logger.debug("[" + callId + "] AdaptiveClientCallListener closing with status: " + status);
            try {
                // Ensure headers are sent before closing
                if (!headersSent) {
                    logger.debug("[" + callId + "] Sending headers before close");
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // Close call to original client
                if (status.isOk()) {
                    logger.debug("[" + callId + "] Call completed successfully, received " + messageCount + " messages");
                } else {
                    logger.warn("[" + callId + "] Call failed with status: " + status.getCode() +
                            ", description: " + status.getDescription() +
                            ", received " + messageCount + " messages");
                }

                serverCall.close(status, trailers);
                logger.debug("[" + callId + "] Server call closed");

                // Cleanup resources
                cleanupStreams();

                // Mark call complete and cleanup resources
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

                // Update active call count
                activeCalls.decrementAndGet();
            } catch (Exception e) {
                logger.error("[" + callId + "] Error closing server call: " + e.getMessage());

                // Ensure count decremented
                activeCalls.decrementAndGet();

                throw e;
            }
        }

        /**
         * Called when the client call is ready to send more messages.
         */
        @Override
        public void onReady() {
            logger.debug("[" + callId + "] AdaptiveClientCallListener ready");
        }

        /**
         * Cleanup resources
         */
        private void cleanupStreams() {
            for (InputStream stream : activeStreams) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore close exceptions
                }
            }
            activeStreams.clear();
        }
    }

    /**
     * Adaptive server call listener, handles requests from original client
     */
    private class AdaptiveServerCallListener extends ServerCall.Listener<InputStream> {
        private final ClientCall<InputStream, InputStream> clientCall;
        private final ServerCall<InputStream, InputStream> serverCall;
        private final String callId;
        private boolean isStreaming;
        private int messageCount = 0;
        private boolean isActive = true;

        // Resource tracking
        private final Set<InputStream> activeStreams = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // Buffer and synchronization
        private final int MAX_BUFFER_SIZE = 20; // Buffer at most 20 messages
        private final List<byte[]> bufferedMessages = new ArrayList<>();
        private final Object bufferLock = new Object();
        private final AtomicBoolean halfCloseSent = new AtomicBoolean(false);
        private volatile int processedMessageCount = 0;
        private long streamHalfCloseTimeoutMs = 500; // Default half-close delay

        // Performance monitoring
        private final long startTime = System.currentTimeMillis();
        private long lastMessageTime = startTime;

        /**
         * Creates a new adaptive server call listener
         *
         * @param clientCall Client call to forward requests to target service
         * @param callId Call ID for logging
         * @param initialIsStreaming Initial streaming detection state
         */
        public AdaptiveServerCallListener(ClientCall<InputStream, InputStream> clientCall,
                                          ServerCall<InputStream, InputStream> serverCall,
                                          String callId, boolean initialIsStreaming) {
            this.clientCall = clientCall;
            this.serverCall = serverCall;
            this.callId = callId;
            this.isStreaming = initialIsStreaming;

            // Update active streaming call count
            if (initialIsStreaming) {
                activeClientStreams.incrementAndGet();
                this.streamHalfCloseTimeoutMs = 500; // Use longer delay for streaming calls
            }

            logger.debug("[" + callId + "] Created new AdaptiveServerCallListener (initial streaming: " + initialIsStreaming + ")");
        }

        /**
         * Mark this listener as handling a client streaming call
         */
        public void markAsClientStreaming() {
            this.isStreaming = true;
            // Increase maximum wait time
            this.streamHalfCloseTimeoutMs = 1000; // Increase to 1000ms for client streams
            logger.debug("[" + callId + "] Marked as client streaming call, half-close timeout set to " +
                    streamHalfCloseTimeoutMs + "ms");
        }

        /**
         * Called when a message is received from the original client.
         * Forwards the message to the target service and updates flow control.
         */
        @Override
        public void onMessage(InputStream message) {
            // Increment global message count
            totalMessagesReceived.incrementAndGet();

            // Check if listener is still active
            if (!isActive) {
                logger.warn("[" + callId + "] Received message after listener was marked inactive");
                try {
                    // Ensure unused message streams are closed
                    message.close();
                } catch (IOException e) {
                    logger.warn("[" + callId + "] Error closing unused message stream: " + e.getMessage());
                }
                return;
            }

            // Track this stream
            activeStreams.add(message);

            // Update message count and time
            messageCount++;
            long now = System.currentTimeMillis();
            long timeSinceLastMessage = now - lastMessageTime;
            lastMessageTime = now;

            logger.debug("[" + callId + "] AdaptiveServerCallListener received message #" + messageCount +
                    " after " + timeSinceLastMessage + "ms");

            try {
                // Read entire message into buffer
                byte[] messageBytes = ByteStreams.toByteArray(message);

                // Add to buffer
                synchronized (bufferLock) {
                    bufferedMessages.add(messageBytes);
                    logger.debug("[" + callId + "] Message #" + messageCount + " added to buffer, buffer size: " +
                            bufferedMessages.size());
                }

                // Update streaming RPC detection
                if (messageCount > 1) {
                    isStreaming = true;
                    // Only increment counter on first detection of streaming behavior
                    if (messageCount == 2) {
                        activeClientStreams.incrementAndGet();
                        // Adjust half-close timeout
                        streamHalfCloseTimeoutMs = Math.max(streamHalfCloseTimeoutMs, 500);
                    }
                }

                // Process buffered messages
                processBufferedMessages();

                // Remove stream object
                activeStreams.remove(message);

                // Request more messages - ensure we always have enough messages requested
                if (messageCount < 10) {
                    serverCall.request(1);
                } else {
                    // For large numbers of messages, request more at once
                    serverCall.request(3);
                }
            } catch (Exception e) {
                logger.error("[" + callId + "] Error processing message: " + e.getMessage());

                // Cleanup resources
                try {
                    message.close();
                } catch (IOException ioe) {
                    // Ignore close exceptions
                }
                activeStreams.remove(message);

                // Mark as error
                Status status = Status.INTERNAL.withDescription("Error processing message: " + e.getMessage());
                serverCall.close(status, new Metadata());
                clientCall.cancel("Error processing message", null);

                // Cleanup resources
                cleanupResources();
                isActive = false;

                // Complete call tracking
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }
            }
        }

        /**
         * Process buffered messages
         */
        private void processBufferedMessages() {
            if (!isActive) return;

            synchronized (bufferLock) {
                // Process all messages in buffer
                while (!bufferedMessages.isEmpty() && isActive) {
                    byte[] messageData = bufferedMessages.get(0);

                    try {
                        // Create new input stream
                        InputStream messageStream = new ByteArrayInputStream(messageData);

                        // Forward message to upstream service
                        clientCall.sendMessage(messageStream);
                        logger.debug("[" + callId + "] Processed message #" + (processedMessageCount + 1) +
                                " of " + messageCount + ", size: " + messageData.length + " bytes");

                        // Update count
                        processedMessageCount++;

                        // Record message processing (for adaptive mechanisms)
                        if (timeoutManager != null) {
                            timeoutManager.recordMessage(callId);
                        }
                        if (flowControlManager != null) {
                            flowControlManager.startProcessingMessage(callId);
                            flowControlManager.completeProcessingMessage(callId);
                        }

                        // Remove processed message
                        bufferedMessages.remove(0);

                        // Request more messages
                        clientCall.request(1);

                    } catch (Exception e) {
                        logger.error("[" + callId + "] Error forwarding buffered message: " + e.getMessage());
                        return; // Stop processing
                    }
                }

                // Check if all messages are processed and half-close signal received
                if (bufferedMessages.isEmpty() && halfCloseSent.get() && isActive) {
                    try {
                        logger.debug("[" + callId + "] All buffered messages processed, sending half-close");
                        clientCall.halfClose();
                        logger.debug("[" + callId + "] Half-close sent to upstream service");
                    } catch (IllegalStateException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already half-closed")) {
                            logger.debug("[" + callId + "] Call already half-closed, ignored");
                        } else {
                            logger.error("[" + callId + "] Error during delayed half-close: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        logger.error("[" + callId + "] Error during delayed half-close: " + e.getMessage());
                    }
                }
            }
        }

        /**
         * Called when the original client has finished sending messages (half-close).
         * Half-closes the call to the target service.
         */
        @Override
        public void onHalfClose() {
            if (!isActive) return;

            try {
                logger.debug("[" + callId + "] AdaptiveServerCallListener half-closing after receiving " +
                        messageCount + " messages");

                // Check if half-close already sent - use AtomicBoolean's compareAndSet for thread safety
                if (!halfCloseSent.compareAndSet(false, true)) {
                    logger.debug("[" + callId + "] Half-close already sent, ignoring duplicate half-close");
                    return; // Half-close already sent, just return
                }

                // Dynamically adjust delay time
                long delayMs = streamHalfCloseTimeoutMs;
                if (messageCount > 5) {
                    // Increase delay for larger message counts
                    delayMs = Math.min(2000, messageCount * 100);
                    logger.debug("[" + callId + "] Adjusted half-close delay to " + delayMs +
                            "ms based on message count: " + messageCount);
                }

                // Remove blocking Thread.sleep() - process messages directly
                // The buffering system will handle proper message sequencing

                synchronized (bufferLock) {
                    // Process all remaining buffered messages
                    processBufferedMessages();

                    // If buffer is empty and half-close not yet sent, send half-close
                    // Note: we check buffer empty again here as processBufferedMessages may have already sent half-close
                    if (bufferedMessages.isEmpty() && isActive) {
                        try {
                            logger.debug("[" + callId + "] Buffer is empty, sending half-close to upstream service");
                            clientCall.halfClose();
                            logger.debug("[" + callId + "] Half-close sent to upstream service");
                        } catch (IllegalStateException e) {
                            if (e.getMessage() != null && e.getMessage().contains("already half-closed")) {
                                logger.debug("[" + callId + "] Call already half-closed, ignored");
                            } else {
                                throw e; // Rethrow other types of IllegalStateException
                            }
                        }
                    } else {
                        logger.debug("[" + callId + "] Delaying half-close until buffer is processed, " +
                                bufferedMessages.size() + " messages remaining");
                    }
                }
            } catch (Exception e) {
                logger.error("[" + callId + "] Error during half-close: " + e.getMessage());

                // Log exception details more thoroughly
                if (e instanceof StatusException) {
                    StatusException se = (StatusException) e;
                    logger.error("[" + callId + "] Status exception during half-close: " +
                            se.getStatus().getCode() + " - " + se.getStatus().getDescription());
                }

                // Ensure call is closed even if half-close fails
                Status status = Status.INTERNAL.withDescription("Error during half-close: " + e.getMessage());
                try {
                    serverCall.close(status, new Metadata());
                } catch (Exception ce) {
                    logger.error("[" + callId + "] Error closing server call: " + ce.getMessage());
                }

                try {
                    clientCall.cancel("Error during half-close", null);
                } catch (Exception ce) {
                    logger.error("[" + callId + "] Error canceling client call: " + ce.getMessage());
                }

                // Cleanup resources
                cleanupResources();
                isActive = false;

                // Complete call tracking
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }
            }
        }

        /**
         * Called when the original client cancels the call.
         * Cancels the call to the target service.
         */
        @Override
        public void onCancel() {
            logger.debug("[" + callId + "] AdaptiveServerCallListener cancelled after receiving " +
                    messageCount + " messages");

            try {
                // Cancel call to target service
                clientCall.cancel("Client cancelled the call", null);
                logger.debug("[" + callId + "] Client call cancelled");
            } catch (Exception e) {
                logger.error("[" + callId + "] Error cancelling client call: " + e.getMessage());
            } finally {
                // Update active stream count
                if (isStreaming) {
                    activeClientStreams.decrementAndGet();
                }

                // Cleanup resources
                cleanupResources();
                isActive = false;

                // Mark call complete and cleanup resources
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

                // Update active call count
                activeCalls.decrementAndGet();
            }
        }

        /**
         * Called when the call from the original client is completed.
         */
        @Override
        public void onComplete() {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.debug("[" + callId + "] AdaptiveServerCallListener completed after receiving " +
                    messageCount + " messages, total time: " + totalTime + "ms");

            try {
                // Update active stream count
                if (isStreaming) {
                    activeClientStreams.decrementAndGet();
                }

                // Cleanup resources
                cleanupResources();
                isActive = false;

                // Mark call complete and cleanup resources
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

                // Update active call count
                activeCalls.decrementAndGet();
            } catch (Exception e) {
                logger.error("[" + callId + "] Error during completion cleanup: " + e.getMessage());
            }
        }

        /**
         * Called when the server call is ready to receive more messages.
         */
        @Override
        public void onReady() {
            if (!isActive) return;

            logger.debug("[" + callId + "] AdaptiveServerCallListener ready");

            // For streaming RPCs, ensure stream remains active
            if (isStreaming && flowControlManager != null) {
                try {
                    flowControlManager.applyFlowControl(clientCall, callId, true);
                } catch (Exception e) {
                    logger.warn("[" + callId + "] Error applying flow control on ready: " + e.getMessage());
                }
            }
        }

        /**
         * Cleanup all active resources
         */
        private void cleanupResources() {
            // Close all active streams
            if (!activeStreams.isEmpty()) {
                logger.debug("[" + callId + "] Cleaning up " + activeStreams.size() + " active streams");

                for (InputStream stream : activeStreams) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // Ignore close exceptions
                        logger.debug("[" + callId + "] Error closing stream: " + e.getMessage());
                    }
                }
                activeStreams.clear();
            }

            // Clear buffer
            synchronized (bufferLock) {
                bufferedMessages.clear();
            }

            // Reset other counters and state
            isActive = false;
        }
    }
}