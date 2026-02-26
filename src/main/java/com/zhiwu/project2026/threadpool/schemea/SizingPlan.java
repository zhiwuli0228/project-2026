package com.zhiwu.project2026.threadpool.schemea;

public record SizingPlan(int corePoolSize, int queueCapacity) {

    public SizingPlan {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
    }
}
