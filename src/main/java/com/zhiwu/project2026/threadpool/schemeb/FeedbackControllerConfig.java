package com.zhiwu.project2026.threadpool.schemeb;

import java.time.Duration;

public record FeedbackControllerConfig(
        int minCore,
        int maxCore,
        int minQueue,
        int maxQueue,
        int queuePerThread,
        long targetQueueWaitP95Ms,
        double busyThreadRatioThreshold,
        int consecutiveQueueBreachThreshold,
        double heapGuardLow,
        double heapGuardHigh,
        long gcPauseP95MsThreshold,
        double maxStepRatio,
        Duration cooldown
) {

    public FeedbackControllerConfig {
        if (minCore <= 0 || maxCore <= 0 || minCore > maxCore) {
            throw new IllegalArgumentException("invalid core bounds");
        }
        if (minQueue <= 0 || maxQueue <= 0 || minQueue > maxQueue) {
            throw new IllegalArgumentException("invalid queue bounds");
        }
        if (queuePerThread <= 0) {
            throw new IllegalArgumentException("queuePerThread must be positive");
        }
        if (targetQueueWaitP95Ms <= 0 || gcPauseP95MsThreshold <= 0) {
            throw new IllegalArgumentException("latency thresholds must be positive");
        }
        if (busyThreadRatioThreshold <= 0 || busyThreadRatioThreshold > 1) {
            throw new IllegalArgumentException("busyThreadRatioThreshold must be in (0, 1]");
        }
        if (consecutiveQueueBreachThreshold <= 0) {
            throw new IllegalArgumentException("consecutiveQueueBreachThreshold must be positive");
        }
        if (heapGuardLow <= 0 || heapGuardLow >= 1 || heapGuardHigh <= 0 || heapGuardHigh >= 1) {
            throw new IllegalArgumentException("heap guards must be in (0, 1)");
        }
        if (heapGuardLow >= heapGuardHigh) {
            throw new IllegalArgumentException("heapGuardLow must be lower than heapGuardHigh");
        }
        if (maxStepRatio <= 0 || maxStepRatio > 1) {
            throw new IllegalArgumentException("maxStepRatio must be in (0, 1]");
        }
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
    }
}
