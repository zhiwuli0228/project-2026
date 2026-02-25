package com.zhiwu.project2026.threadpool.schemeb;

public record FeedbackSizingDecision(
        int targetCorePoolSize,
        int targetQueueCapacity,
        ScalingAction action,
        String reason
) {
}
