package com.zhiwu.project2026.threadpool.schemed;

public record ModelFeatures(
        double qps,
        long queueWaitP95Ms,
        long execTimeP95Ms,
        double activeRatio,
        double rejectionRate,
        double heapUsedRatio,
        long gcPauseP95Ms,
        long xmxBytes,
        int replicaCount,
        int availableProcessors
) {
    public ModelFeatures {
        if (qps < 0) {
            throw new IllegalArgumentException("qps must be non-negative");
        }
        if (queueWaitP95Ms < 0 || execTimeP95Ms < 0 || gcPauseP95Ms < 0) {
            throw new IllegalArgumentException("latency values must be non-negative");
        }
        if (activeRatio < 0 || activeRatio > 1) {
            throw new IllegalArgumentException("activeRatio must be in [0, 1]");
        }
        if (rejectionRate < 0 || rejectionRate > 1) {
            throw new IllegalArgumentException("rejectionRate must be in [0, 1]");
        }
        if (heapUsedRatio < 0 || heapUsedRatio > 1) {
            throw new IllegalArgumentException("heapUsedRatio must be in [0, 1]");
        }
        if (xmxBytes <= 0) {
            throw new IllegalArgumentException("xmxBytes must be positive");
        }
        if (replicaCount <= 0 || availableProcessors <= 0) {
            throw new IllegalArgumentException("replicaCount and availableProcessors must be positive");
        }
    }
}
