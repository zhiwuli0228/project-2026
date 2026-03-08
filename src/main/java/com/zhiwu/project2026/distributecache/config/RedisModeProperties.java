package com.zhiwu.project2026.distributecache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "distributecache.redis")
public class RedisModeProperties {

    /**
     * single | cluster
     */
    private String mode = "single";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isClusterMode() {
        return "cluster".equalsIgnoreCase(mode);
    }
}

