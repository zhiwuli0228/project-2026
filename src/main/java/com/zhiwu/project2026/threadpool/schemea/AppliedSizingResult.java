package com.zhiwu.project2026.threadpool.schemea;

import java.util.Objects;

public record AppliedSizingResult(
        SizingPlan planned,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {

    public AppliedSizingResult {
        Objects.requireNonNull(planned, "planned");
        if (appliedQueueCapacity <= 0) {
            throw new IllegalArgumentException("appliedQueueCapacity must be positive");
        }
        if (queueShrinkDeferred && appliedQueueCapacity <= planned.queueCapacity()) {
            throw new IllegalArgumentException("deferred shrink requires appliedQueueCapacity > planned.queueCapacity");
        }
        if (!queueShrinkDeferred && appliedQueueCapacity != planned.queueCapacity()) {
            throw new IllegalArgumentException("non-deferred result must match planned queue capacity");
        }
    }
}
