package com.zhiwu.project2026.threadpool.schemec;

import java.util.Objects;

public record HybridSizingDecision(
        int targetCorePoolSize,
        int targetQueueCapacity,
        HybridScalingAction action,
        String reason
) {
    public HybridSizingDecision {
        if (targetCorePoolSize <= 0) {
            throw new IllegalArgumentException("targetCorePoolSize must be positive");
        }
        if (targetQueueCapacity <= 0) {
            throw new IllegalArgumentException("targetQueueCapacity must be positive");
        }
        action = Objects.requireNonNull(action, "action");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
