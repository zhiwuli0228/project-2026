package com.zhiwu.project2026.threadpool.schemec;

import java.time.Instant;
import java.util.Objects;

public class HybridSizingController {

    private final HybridSizingConfig config;

    public HybridSizingController(HybridSizingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public HybridSizingDecision decide(
            int currentCorePoolSize,
            HybridBudgetPlan budget,
            HybridMetrics metrics,
            HybridControlState state,
            Instant now
    ) {
        if (currentCorePoolSize <= 0) {
            throw new IllegalArgumentException("currentCorePoolSize must be positive");
        }
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        if (metrics.queueWaitP95Ms() > config.targetQueueWaitP95Ms()) {
            state.incrementQueueWaitBreaches();
        } else {
            state.resetQueueWaitBreaches();
        }

        int clampedCurrentCore = clamp(budget.coreMin(), currentCorePoolSize, budget.coreMax());
        int step = Math.max(1, (int) Math.floor(clampedCurrentCore * config.maxStepRatio()));

        if (!cooldownPassed(state, now)) {
            return new HybridSizingDecision(
                    clampedCurrentCore,
                    queueByCoreWithinBudget(clampedCurrentCore, budget),
                    HybridScalingAction.HOLD_COOLDOWN,
                    "cooldown not passed"
            );
        }

        if (metrics.heapUsedRatio() > config.heapGuardHigh()
                || metrics.gcPauseP95Ms() > config.gcPauseP95MsThreshold()) {
            int targetCore = clamp(budget.coreMin(), clampedCurrentCore - step, budget.coreMax());
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new HybridSizingDecision(
                    targetCore,
                    queueByCoreWithinBudget(targetCore, budget),
                    HybridScalingAction.SCALE_DOWN,
                    "heap or gc pressure high"
            );
        }

        boolean shouldScaleUp = state.getConsecutiveQueueWaitBreaches() >= config.consecutiveQueueBreachThreshold()
                && metrics.activeRatio() >= config.busyThreadRatioThreshold()
                && metrics.heapUsedRatio() < config.heapGuardLow();
        if (shouldScaleUp) {
            int targetCore = clamp(budget.coreMin(), clampedCurrentCore + step, budget.coreMax());
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new HybridSizingDecision(
                    targetCore,
                    queueByCoreWithinBudget(targetCore, budget),
                    HybridScalingAction.SCALE_UP,
                    "queue wait breach persisted"
            );
        }

        return new HybridSizingDecision(
                clampedCurrentCore,
                queueByCoreWithinBudget(clampedCurrentCore, budget),
                HybridScalingAction.HOLD_STABLE,
                "no scaling condition met"
        );
    }

    private boolean cooldownPassed(HybridControlState state, Instant now) {
        Instant lastAdjustmentAt = state.getLastAdjustmentAt();
        return lastAdjustmentAt == null || !lastAdjustmentAt.plus(config.cooldown()).isAfter(now);
    }

    private int queueByCoreWithinBudget(int corePoolSize, HybridBudgetPlan budget) {
        int targetRatio = (config.queuePerThreadLower() + config.queuePerThreadUpper()) / 2;
        int estimatedQueue = corePoolSize * targetRatio;
        return clamp(budget.queueMin(), estimatedQueue, budget.queueMax());
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
