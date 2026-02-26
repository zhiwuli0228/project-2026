package com.zhiwu.project2026.threadpool.schemed;

import java.util.Objects;

public record AppliedModelResult(
        ModelSizingDecision decision,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
    public AppliedModelResult {
        decision = Objects.requireNonNull(decision, "decision");
        if (appliedQueueCapacity <= 0) {
            throw new IllegalArgumentException("appliedQueueCapacity must be positive");
        }
        if (queueShrinkDeferred && appliedQueueCapacity <= decision.targetQueueCapacity()) {
            throw new IllegalArgumentException("deferred shrink requires appliedQueueCapacity > targetQueueCapacity");
        }
        if (!queueShrinkDeferred && appliedQueueCapacity != decision.targetQueueCapacity()) {
            throw new IllegalArgumentException("non-deferred result must match target queue capacity");
        }
    }
}
