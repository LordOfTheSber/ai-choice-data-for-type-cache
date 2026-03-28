package com.example.cache.master.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache.serialization")
public class SerializationProperties {
    private boolean compressionEnabled = true;
    private int compressionThresholdBytes = 8 * 1024;
    private int outputBufferInitialBytes = 512;
    private int outputBufferMaxBytes = 4 * 1024 * 1024;

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public int getCompressionThresholdBytes() {
        return compressionThresholdBytes;
    }

    public void setCompressionThresholdBytes(int compressionThresholdBytes) {
        this.compressionThresholdBytes = compressionThresholdBytes;
    }

    public int getOutputBufferInitialBytes() {
        return outputBufferInitialBytes;
    }

    public void setOutputBufferInitialBytes(int outputBufferInitialBytes) {
        this.outputBufferInitialBytes = outputBufferInitialBytes;
    }

    public int getOutputBufferMaxBytes() {
        return outputBufferMaxBytes;
    }

    public void setOutputBufferMaxBytes(int outputBufferMaxBytes) {
        this.outputBufferMaxBytes = outputBufferMaxBytes;
    }
}
