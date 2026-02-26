package com.zhiwu.project2026.threadpool.gating;

public record ScaleUpGateInput(
        long queueWaitP95Ms,
        double activeRatio,
        double heapUsedRatio,
        long gcPauseP95Ms
) {
    public ScaleUpGateInput {
        if (queueWaitP95Ms < 0 || gcPauseP95Ms < 0) {
            throw new IllegalArgumentException("latency values must be non-negative");
        }
        if (activeRatio < 0 || activeRatio > 1) {
            throw new IllegalArgumentException("activeRatio must be in [0, 1]");
        }
        if (heapUsedRatio < 0 || heapUsedRatio > 1) {
            throw new IllegalArgumentException("heapUsedRatio must be in [0, 1]");
        }
    }
}
