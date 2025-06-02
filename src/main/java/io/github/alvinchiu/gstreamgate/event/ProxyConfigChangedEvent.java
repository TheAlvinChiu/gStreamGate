package io.github.alvinchiu.gstreamgate.event;

/**
 * Event class indicating that proxy configuration has changed.
 * This event is published when a proxy mapping is added, updated, or deleted.
 */
public class ProxyConfigChangedEvent {

    // Event type enum
    public enum ChangeType {
        ADDED, UPDATED, REMOVED, REFRESHED
    }

    private final ChangeType changeType;
    private final String proxyHostname;

    /**
     * Creates a proxy configuration change event.
     *
     * @param changeType The type of change
     * @param proxyHostname The proxy hostname that changed
     */
    public ProxyConfigChangedEvent(ChangeType changeType, String proxyHostname) {
        this.changeType = changeType;
        this.proxyHostname = proxyHostname;
    }

    /**
     * Creates a refresh all configurations event.
     *
     * @return Refresh event
     */
    public static ProxyConfigChangedEvent refreshEvent() {
        return new ProxyConfigChangedEvent(ChangeType.REFRESHED, null);
    }

    /**
     * Gets the change type.
     *
     * @return Change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Gets the proxy hostname that changed.
     * For REFRESHED type events, this may return null.
     *
     * @return Proxy hostname
     */
    public String getProxyHostname() {
        return proxyHostname;
    }

    @Override
    public String toString() {
        return "ProxyConfigChangedEvent{" +
                "changeType=" + changeType +
                ", proxyHostname='" + proxyHostname + '\'' +
                '}';
    }
}