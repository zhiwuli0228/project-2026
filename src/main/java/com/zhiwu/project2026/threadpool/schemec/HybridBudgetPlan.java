package com.zhiwu.project2026.threadpool.schemec;

public record HybridBudgetPlan(
        int coreMin,
        int coreMax,
        int queueMin,
        int queueMax
) {
}
