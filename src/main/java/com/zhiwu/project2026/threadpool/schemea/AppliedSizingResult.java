package com.zhiwu.project2026.threadpool.schemea;

public record AppliedSizingResult(
        SizingPlan planned,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
}

