package com.zhiwu.project2026.threadpool.schemeb;

import java.util.Objects;

public record FeedbackSizingDecision(
        int targetCorePoolSize,
        int targetQueueCapacity,
        ScalingAction action,
        String reason
) {
    public FeedbackSizingDecision {
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
