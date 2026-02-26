package com.zhiwu.project2026.threadpool.schemec;

import java.util.Objects;

public record AppliedHybridResult(
        HybridBudgetPlan budget,
        HybridSizingDecision decision,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
    public AppliedHybridResult {
        budget = Objects.requireNonNull(budget, "budget");
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
