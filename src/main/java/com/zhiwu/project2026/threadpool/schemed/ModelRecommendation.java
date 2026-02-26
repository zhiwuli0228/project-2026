package com.zhiwu.project2026.threadpool.schemed;

public record ModelRecommendation(
        int recommendedCorePoolSize,
        int recommendedQueueCapacity,
        double rawCoreScore,
        double rawQueueScore
) {
    public ModelRecommendation {
        if (recommendedCorePoolSize <= 0) {
            throw new IllegalArgumentException("recommendedCorePoolSize must be positive");
        }
        if (recommendedQueueCapacity <= 0) {
            throw new IllegalArgumentException("recommendedQueueCapacity must be positive");
        }
        if (!Double.isFinite(rawCoreScore) || !Double.isFinite(rawQueueScore)) {
            throw new IllegalArgumentException("raw scores must be finite");
        }
    }
}
