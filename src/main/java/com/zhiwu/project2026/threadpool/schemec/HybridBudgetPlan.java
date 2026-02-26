package com.zhiwu.project2026.threadpool.schemec;

public record HybridBudgetPlan(
        int coreMin,
        int coreMax,
        int queueMin,
        int queueMax
) {
    public HybridBudgetPlan {
        if (coreMin <= 0 || coreMax <= 0 || coreMin > coreMax) {
            throw new IllegalArgumentException("invalid core budget bounds");
        }
        if (queueMin <= 0 || queueMax <= 0 || queueMin > queueMax) {
            throw new IllegalArgumentException("invalid queue budget bounds");
        }
    }
}
