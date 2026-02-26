package com.zhiwu.project2026.threadpool.gating;

public record ScaleUpGateConfig(
        long targetQueueWaitP95Ms,
        double busyThreadRatioThreshold,
        double heapGuardLow,
        long gcPauseP95MsSoftThreshold,
        int consecutiveQueueBreachThreshold
) {
    public ScaleUpGateConfig {
        if (targetQueueWaitP95Ms <= 0 || gcPauseP95MsSoftThreshold <= 0) {
            throw new IllegalArgumentException("latency thresholds must be positive");
        }
        if (busyThreadRatioThreshold <= 0 || busyThreadRatioThreshold > 1) {
            throw new IllegalArgumentException("busyThreadRatioThreshold must be in (0, 1]");
        }
        if (heapGuardLow <= 0 || heapGuardLow >= 1) {
            throw new IllegalArgumentException("heapGuardLow must be in (0, 1)");
        }
        if (consecutiveQueueBreachThreshold <= 0) {
            throw new IllegalArgumentException("consecutiveQueueBreachThreshold must be positive");
        }
    }
}
