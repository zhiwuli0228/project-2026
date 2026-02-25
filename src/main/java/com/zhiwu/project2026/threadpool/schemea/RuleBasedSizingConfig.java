package com.zhiwu.project2026.threadpool.schemea;

public record RuleBasedSizingConfig(
        int minCore,
        int maxCore,
        double cpuCoreMultiplier,
        double memBudgetRatio,
        int queuePerThread,
        int minQueue,
        int maxQueue,
        int referenceReplica
) {

    public RuleBasedSizingConfig {
        if (minCore <= 0 || maxCore <= 0 || minCore > maxCore) {
            throw new IllegalArgumentException("invalid core bounds");
        }
        if (cpuCoreMultiplier <= 0 || memBudgetRatio <= 0) {
            throw new IllegalArgumentException("multiplier and budget ratio must be positive");
        }
        if (queuePerThread <= 0 || minQueue <= 0 || maxQueue <= 0 || minQueue > maxQueue) {
            throw new IllegalArgumentException("invalid queue bounds");
        }
        if (referenceReplica <= 0) {
            throw new IllegalArgumentException("referenceReplica must be positive");
        }
    }
}

