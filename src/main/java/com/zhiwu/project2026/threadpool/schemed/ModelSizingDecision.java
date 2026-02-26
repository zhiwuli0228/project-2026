package com.zhiwu.project2026.threadpool.schemed;

import java.util.Objects;

public record ModelSizingDecision(
        int targetCorePoolSize,
        int targetQueueCapacity,
        ModelScalingAction action,
        String reason
) {
    public ModelSizingDecision {
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
