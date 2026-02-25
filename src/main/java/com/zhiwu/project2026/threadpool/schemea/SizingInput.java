package com.zhiwu.project2026.threadpool.schemea;

public record SizingInput(
        long xmxBytes,
        int replicaCount,
        long taskP95HeapBytes,
        int availableProcessors
) {

    public SizingInput {
        if (xmxBytes <= 0 || taskP95HeapBytes <= 0) {
            throw new IllegalArgumentException("xmxBytes and taskP95HeapBytes must be positive");
        }
        if (replicaCount <= 0 || availableProcessors <= 0) {
            throw new IllegalArgumentException("replicaCount and availableProcessors must be positive");
        }
    }
}

