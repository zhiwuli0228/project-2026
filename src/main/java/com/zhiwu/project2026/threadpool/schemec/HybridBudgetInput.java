package com.zhiwu.project2026.threadpool.schemec;

public record HybridBudgetInput(
        long xmxBytes,
        int replicaCount,
        long taskAvgHeapBytes,
        long taskP95HeapBytes,
        int availableProcessors
) {

    public HybridBudgetInput {
        if (xmxBytes <= 0 || taskAvgHeapBytes <= 0 || taskP95HeapBytes <= 0) {
            throw new IllegalArgumentException("heap values must be positive");
        }
        if (replicaCount <= 0 || availableProcessors <= 0) {
            throw new IllegalArgumentException("replicaCount and availableProcessors must be positive");
        }
    }
}
