package com.example.cache.balancer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "heartbeat")
public class HeartbeatProperties {

    private long timeoutMillis = 5_000;
    private long checkIntervalMillis = 2_000;

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long getCheckIntervalMillis() {
        return checkIntervalMillis;
    }

    public void setCheckIntervalMillis(long checkIntervalMillis) {
        this.checkIntervalMillis = checkIntervalMillis;
    }
}
