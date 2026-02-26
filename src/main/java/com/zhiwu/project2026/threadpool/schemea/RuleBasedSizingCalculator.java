package com.zhiwu.project2026.threadpool.schemea;

import java.util.Objects;

public class RuleBasedSizingCalculator {

    private final RuleBasedSizingConfig config;

    public RuleBasedSizingCalculator(RuleBasedSizingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SizingPlan calculate(SizingInput input) {
        int baseCore = clamp(config.minCore(),
                (int) Math.floor(input.availableProcessors() * config.cpuCoreMultiplier()),
                config.maxCore());

        int memBoundCore = Math.max(1,
                (int) Math.floor((input.xmxBytes() * config.memBudgetRatio()) / input.taskP95HeapBytes()));
        double replicaFactor = Math.sqrt((double) config.referenceReplica() / input.replicaCount());

        int core = clamp(config.minCore(),
                (int) Math.floor(Math.min(baseCore, memBoundCore) * replicaFactor),
                config.maxCore());
        long rawQueue = (long) core * config.queuePerThread();
        int queue = clamp(config.minQueue(),
                (int) Math.min(Integer.MAX_VALUE, rawQueue),
                config.maxQueue());

        return new SizingPlan(core, queue);
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
