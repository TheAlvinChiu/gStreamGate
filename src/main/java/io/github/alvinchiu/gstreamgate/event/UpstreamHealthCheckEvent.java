package io.github.alvinchiu.gstreamgate.event;

/**
 * Event class for upstream service health check results
 */
public class UpstreamHealthCheckEvent {
    private final String proxyHostname;
    private final String targetHostname;
    private final int targetPort;
    private final boolean healthy;
    private final String message;

    /**
     * Creates a new health check event
     *
     * @param proxyHostname The proxy hostname
     * @param targetHostname The target hostname
     * @param targetPort The target port
     * @param healthy Whether the target is healthy
     * @param message Additional message providing details about the health check
     */
    public UpstreamHealthCheckEvent(String proxyHostname, String targetHostname, int targetPort,
                                    boolean healthy, String message) {
        this.proxyHostname = proxyHostname;
        this.targetHostname = targetHostname;
        this.targetPort = targetPort;
        this.healthy = healthy;
        this.message = message;
    }

    /**
     * Gets the proxy hostname
     *
     * @return The proxy hostname
     */
    public String getProxyHostname() {
        return proxyHostname;
    }

    /**
     * Gets the target hostname
     *
     * @return The target hostname
     */
    public String getTargetHostname() {
        return targetHostname;
    }

    /**
     * Gets the target port
     *
     * @return The target port
     */
    public int getTargetPort() {
        return targetPort;
    }

    /**
     * Gets whether the target is healthy
     *
     * @return true if the target is healthy, false otherwise
     */
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * Gets the health check message
     *
     * @return The health check message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "UpstreamHealthCheckEvent{" +
                "proxyHostname='" + proxyHostname + '\'' +
                ", targetHostname='" + targetHostname + '\'' +
                ", targetPort=" + targetPort +
                ", healthy=" + healthy +
                ", message='" + message + '\'' +
                '}';
    }
}