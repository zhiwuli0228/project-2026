package com.zhiwu.project2026.distributecache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "distributecache.degrade")
public class DegradeProperties {

    private boolean redisEnabled = true;
    private int dbFallbackMaxConcurrent = 64;

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public int getDbFallbackMaxConcurrent() {
        return dbFallbackMaxConcurrent;
    }

    public void setDbFallbackMaxConcurrent(int dbFallbackMaxConcurrent) {
        this.dbFallbackMaxConcurrent = dbFallbackMaxConcurrent;
    }
}

