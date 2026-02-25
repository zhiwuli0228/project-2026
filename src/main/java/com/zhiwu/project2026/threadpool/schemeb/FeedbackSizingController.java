package com.zhiwu.project2026.threadpool.schemeb;

import java.time.Instant;
import java.util.Objects;

public class FeedbackSizingController {

    private final FeedbackControllerConfig config;

    public FeedbackSizingController(FeedbackControllerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public FeedbackSizingDecision decide(
            int currentCorePoolSize,
            FeedbackMetrics metrics,
            FeedbackControlState state,
            Instant now
    ) {
        if (currentCorePoolSize <= 0) {
            throw new IllegalArgumentException("currentCorePoolSize must be positive");
        }
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        if (metrics.queueWaitP95Ms() > config.targetQueueWaitP95Ms()) {
            state.incrementQueueWaitBreaches();
        } else {
            state.resetQueueWaitBreaches();
        }

        int clampedCurrentCore = clamp(config.minCore(), currentCorePoolSize, config.maxCore());
        int step = Math.max(1, (int) Math.floor(clampedCurrentCore * config.maxStepRatio()));

        if (!cooldownPassed(state, now)) {
            int currentQueue = queueByCore(clampedCurrentCore);
            return new FeedbackSizingDecision(
                    clampedCurrentCore,
                    currentQueue,
                    ScalingAction.HOLD_COOLDOWN,
                    "cooldown not passed"
            );
        }

        if (metrics.heapUsedRatio() > config.heapGuardHigh()
                || metrics.gcPauseP95Ms() > config.gcPauseP95MsThreshold()) {
            int targetCore = clamp(config.minCore(), clampedCurrentCore - step, config.maxCore());
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new FeedbackSizingDecision(
                    targetCore,
                    queueByCore(targetCore),
                    ScalingAction.SCALE_DOWN,
                    "heap or gc pressure high"
            );
        }

        boolean shouldScaleUp = state.getConsecutiveQueueWaitBreaches() >= config.consecutiveQueueBreachThreshold()
                && metrics.activeRatio() >= config.busyThreadRatioThreshold()
                && metrics.heapUsedRatio() < config.heapGuardLow();
        if (shouldScaleUp) {
            int targetCore = clamp(config.minCore(), clampedCurrentCore + step, config.maxCore());
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new FeedbackSizingDecision(
                    targetCore,
                    queueByCore(targetCore),
                    ScalingAction.SCALE_UP,
                    "queue wait breach persisted"
            );
        }

        return new FeedbackSizingDecision(
                clampedCurrentCore,
                queueByCore(clampedCurrentCore),
                ScalingAction.HOLD_STABLE,
                "no scaling condition met"
        );
    }

    private boolean cooldownPassed(FeedbackControlState state, Instant now) {
        Instant lastAdjustmentAt = state.getLastAdjustmentAt();
        return lastAdjustmentAt == null
                || !lastAdjustmentAt.plus(config.cooldown()).isAfter(now);
    }

    private int queueByCore(int corePoolSize) {
        int linked = corePoolSize * config.queuePerThread();
        return clamp(config.minQueue(), linked, config.maxQueue());
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
