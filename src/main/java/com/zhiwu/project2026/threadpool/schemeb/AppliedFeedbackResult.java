package com.zhiwu.project2026.threadpool.schemeb;

public record AppliedFeedbackResult(
        FeedbackSizingDecision decision,
        int appliedQueueCapacity,
        boolean queueShrinkDeferred
) {
}
