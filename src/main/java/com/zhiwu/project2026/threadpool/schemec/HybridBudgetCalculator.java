package com.zhiwu.project2026.threadpool.schemec;

import java.util.Objects;

public class HybridBudgetCalculator {

    private static final int DEFAULT_TARGET_QUEUE_PER_THREAD = 100;

    private final HybridSizingConfig config;

    public HybridBudgetCalculator(HybridSizingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public HybridBudgetPlan calculate(HybridBudgetInput input) {
        Objects.requireNonNull(input, "input");

        int coreUpperByMem = Math.max(1,
                (int) Math.floor((input.xmxBytes() * config.memBudgetRatio()) / input.taskP95HeapBytes()));
        int coreUpperByCpu = Math.max(1,
                (int) Math.floor(input.availableProcessors() * config.cpuThreadFactor()));

        int baseCoreMax = clamp(config.hardMinCore(),
                Math.min(coreUpperByMem, coreUpperByCpu),
                config.hardMaxCore());
        int baseCoreMin = Math.max(config.hardMinCore(),
                (int) Math.floor(baseCoreMax * config.minCoreRatio()));

        double scaleFactor = Math.pow((double) config.referenceReplica() / input.replicaCount(), config.alpha());
        int scaledCoreMin = clamp(config.hardMinCore(),
                (int) Math.ceil(baseCoreMin * scaleFactor),
                config.hardMaxCore());
        int scaledCoreMax = clamp(config.hardMinCore(),
                (int) Math.floor(baseCoreMax * scaleFactor),
                config.hardMaxCore());
        if (scaledCoreMin > scaledCoreMax) {
            scaledCoreMin = scaledCoreMax;
        }

        int queueMaxByMem = Math.max(1,
                (int) Math.floor((input.xmxBytes() * config.queueMemBudgetRatio()) / input.taskAvgHeapBytes()));
        int queueMax = Math.min(
                queueMaxByMem,
                scaledCoreMax * config.queuePerThreadUpper()
        );
        int queueTargetPerThread = clamp(
                config.queuePerThreadLower(),
                DEFAULT_TARGET_QUEUE_PER_THREAD,
                config.queuePerThreadUpper()
        );
        int queueMaxFloorByCore = scaledCoreMax > config.hardMinCore()
                ? scaledCoreMax * queueTargetPerThread
                : config.hardMinQueue();
        queueMax = Math.max(queueMax, queueMaxFloorByCore);
        queueMax = clamp(config.hardMinQueue(), queueMax, config.hardMaxQueue());

        int queueMin = Math.max(config.hardMinQueue(), scaledCoreMin * config.queuePerThreadLower());
        queueMin = clamp(config.hardMinQueue(), queueMin, config.hardMaxQueue());
        if (queueMin > queueMax) {
            queueMin = queueMax;
        }

        return new HybridBudgetPlan(scaledCoreMin, scaledCoreMax, queueMin, queueMax);
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
