package com.zhiwu.project2026.threadpool.schemec;

import java.time.Duration;

public record HybridSizingConfig(
        int hardMinCore,
        int hardMaxCore,
        int hardMinQueue,
        int hardMaxQueue,
        double memBudgetRatio,
        double queueMemBudgetRatio,
        double cpuThreadFactor,
        double minCoreRatio,
        int queuePerThreadLower,
        int queuePerThreadUpper,
        int referenceReplica,
        double alpha,
        long targetQueueWaitP95Ms,
        double busyThreadRatioThreshold,
        int consecutiveQueueBreachThreshold,
        double heapGuardLow,
        double heapGuardHigh,
        long gcPauseP95MsThreshold,
        double maxStepRatio,
        Duration cooldown
) {

    public HybridSizingConfig {
        if (hardMinCore <= 0 || hardMaxCore <= 0 || hardMinCore > hardMaxCore) {
            throw new IllegalArgumentException("invalid core hard bounds");
        }
        if (hardMinQueue <= 0 || hardMaxQueue <= 0 || hardMinQueue > hardMaxQueue) {
            throw new IllegalArgumentException("invalid queue hard bounds");
        }
        if (memBudgetRatio <= 0 || queueMemBudgetRatio <= 0 || cpuThreadFactor <= 0 || minCoreRatio <= 0 || minCoreRatio > 1) {
            throw new IllegalArgumentException("invalid budget ratios");
        }
        if (queuePerThreadLower <= 0 || queuePerThreadUpper <= 0 || queuePerThreadLower > queuePerThreadUpper) {
            throw new IllegalArgumentException("invalid queue per thread bounds");
        }
        if (referenceReplica <= 0 || alpha <= 0) {
            throw new IllegalArgumentException("referenceReplica and alpha must be positive");
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
        if (heapGuardLow <= 0 || heapGuardLow >= 1 || heapGuardHigh <= 0 || heapGuardHigh >= 1 || heapGuardLow >= heapGuardHigh) {
            throw new IllegalArgumentException("invalid heap guards");
        }
        if (maxStepRatio <= 0 || maxStepRatio > 1) {
            throw new IllegalArgumentException("maxStepRatio must be in (0, 1]");
        }
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
    }
}
