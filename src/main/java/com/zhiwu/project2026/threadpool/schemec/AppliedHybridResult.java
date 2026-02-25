package com.zhiwu.project2026.threadpool.schemec;

public record AppliedHybridResult(
        HybridBudgetPlan budget,
        HybridSizingDecision decision,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
}
