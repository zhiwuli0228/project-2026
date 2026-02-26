package com.zhiwu.project2026.threadpool.gating;

import java.util.Objects;

public class DefaultScaleUpGate implements ScaleUpGate {

    private final ScaleUpGateConfig config;

    public DefaultScaleUpGate(ScaleUpGateConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public ScaleUpGateVerdict evaluate(ScaleUpGateInput input, ScaleUpGateState state) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(state, "state");

        if (input.queueWaitP95Ms() <= config.targetQueueWaitP95Ms()) {
            state.resetQueueWaitBreaches();
            return new ScaleUpGateVerdict(false, PressureLevel.NORMAL, "queue wait below target");
        }

        state.incrementQueueWaitBreaches();
        int breaches = state.getConsecutiveQueueWaitBreaches();
        PressureLevel level = breaches >= config.consecutiveQueueBreachThreshold()
                ? PressureLevel.CRITICAL
                : PressureLevel.ELEVATED;

        if (breaches < config.consecutiveQueueBreachThreshold()) {
            return new ScaleUpGateVerdict(false, level, "insufficient consecutive queue breaches");
        }
        if (input.activeRatio() < config.busyThreadRatioThreshold()) {
            return new ScaleUpGateVerdict(false, level, "active ratio below busy threshold");
        }
        if (input.heapUsedRatio() >= config.heapGuardLow()) {
            return new ScaleUpGateVerdict(false, level, "heap used ratio above low guard");
        }
        if (input.gcPauseP95Ms() >= config.gcPauseP95MsSoftThreshold()) {
            return new ScaleUpGateVerdict(false, level, "gc pause above soft threshold");
        }
        return new ScaleUpGateVerdict(true, PressureLevel.CRITICAL, "scale-up gate passed");
    }
}
