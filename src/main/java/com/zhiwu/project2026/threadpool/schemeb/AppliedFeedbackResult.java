package com.zhiwu.project2026.threadpool.schemeb;

import java.util.Objects;

public record AppliedFeedbackResult(
        FeedbackSizingDecision decision,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
    public AppliedFeedbackResult {
        decision = Objects.requireNonNull(decision, "decision");
        if (appliedQueueCapacity <= 0) {
            throw new IllegalArgumentException("appliedQueueCapacity must be positive");
        }
    }
}
