package com.zhiwu.project2026.threadpool.schemec;

public record HybridSizingDecision(
        int targetCorePoolSize,
        int targetQueueCapacity,
        HybridScalingAction action,
        String reason
) {
}
