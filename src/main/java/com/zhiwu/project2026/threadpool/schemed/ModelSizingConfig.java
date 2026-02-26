package com.zhiwu.project2026.threadpool.schemed;

import java.time.Duration;

public record ModelSizingConfig(
        int minCore,
        int maxCore,
        int minQueue,
        int maxQueue,
        int queuePerThreadBaseline,
        long targetQueueWaitP95Ms,
        double busyThreadRatioThreshold,
        int consecutiveQueueBreachThreshold,
        double heapGuardLow,
        double maxStepRatio,
        double heapGuardHigh,
        long gcPauseP95MsThreshold,
        Duration cooldown
) {
    public ModelSizingConfig {
        if (minCore <= 0 || maxCore <= 0 || minCore > maxCore) {
            throw new IllegalArgumentException("invalid core bounds");
        }
        if (minQueue <= 0 || maxQueue <= 0 || minQueue > maxQueue) {
            throw new IllegalArgumentException("invalid queue bounds");
        }
        if (queuePerThreadBaseline <= 0) {
            throw new IllegalArgumentException("queuePerThreadBaseline must be positive");
        }
        if (targetQueueWaitP95Ms <= 0) {
            throw new IllegalArgumentException("targetQueueWaitP95Ms must be positive");
        }
        if (busyThreadRatioThreshold <= 0 || busyThreadRatioThreshold > 1) {
            throw new IllegalArgumentException("busyThreadRatioThreshold must be in (0, 1]");
        }
        if (consecutiveQueueBreachThreshold <= 0) {
            throw new IllegalArgumentException("consecutiveQueueBreachThreshold must be positive");
        }
        if (heapGuardLow <= 0 || heapGuardLow >= 1) {
            throw new IllegalArgumentException("heapGuardLow must be in (0, 1)");
        }
        if (maxStepRatio <= 0 || maxStepRatio > 1) {
            throw new IllegalArgumentException("maxStepRatio must be in (0, 1]");
        }
        if (heapGuardHigh <= 0 || heapGuardHigh >= 1) {
            throw new IllegalArgumentException("heapGuardHigh must be in (0, 1)");
        }
        if (heapGuardLow >= heapGuardHigh) {
            throw new IllegalArgumentException("heapGuardLow must be lower than heapGuardHigh");
        }
        if (gcPauseP95MsThreshold <= 0) {
            throw new IllegalArgumentException("gcPauseP95MsThreshold must be positive");
        }
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
    }
}
