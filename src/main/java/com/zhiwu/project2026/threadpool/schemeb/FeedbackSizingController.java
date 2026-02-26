package com.zhiwu.project2026.threadpool.schemeb;

import com.zhiwu.project2026.threadpool.gating.DefaultScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateConfig;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateVerdict;

import java.time.Instant;
import java.util.Objects;

public class FeedbackSizingController {

    private final FeedbackControllerConfig config;
    private final ScaleUpGate scaleUpGate;

    public FeedbackSizingController(FeedbackControllerConfig config) {
        this(
                config,
                new DefaultScaleUpGate(
                        new ScaleUpGateConfig(
                                config.targetQueueWaitP95Ms(),
                                config.busyThreadRatioThreshold(),
                                config.heapGuardLow(),
                                config.gcPauseP95MsThreshold(),
                                config.consecutiveQueueBreachThreshold()
                        )
                )
        );
    }

    public FeedbackSizingController(FeedbackControllerConfig config, ScaleUpGate scaleUpGate) {
        this.config = Objects.requireNonNull(config, "config");
        this.scaleUpGate = Objects.requireNonNull(scaleUpGate, "scaleUpGate");
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
            if (targetCore == clampedCurrentCore) {
                return holdStable(clampedCurrentCore, "downscale blocked at minCore");
            }
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new FeedbackSizingDecision(
                    targetCore,
                    queueByCore(targetCore),
                    ScalingAction.SCALE_DOWN,
                    "heap or gc pressure high"
            );
        }

        ScaleUpGateVerdict gateVerdict = scaleUpGate.evaluate(
                new ScaleUpGateInput(
                        metrics.queueWaitP95Ms(),
                        metrics.activeRatio(),
                        metrics.heapUsedRatio(),
                        metrics.gcPauseP95Ms()
                ),
                state.getScaleUpGateState()
        );
        if (gateVerdict.allowScaleUp()) {
            int targetCore = clamp(config.minCore(), clampedCurrentCore + step, config.maxCore());
            if (targetCore == clampedCurrentCore) {
                return holdStable(clampedCurrentCore, "upscale blocked at maxCore");
            }
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new FeedbackSizingDecision(
                    targetCore,
                    queueByCore(targetCore),
                    ScalingAction.SCALE_UP,
                    "scale-up gate passed"
            );
        }

        return holdStable(clampedCurrentCore, "scale-up gate blocked: " + gateVerdict.reason());
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

    private FeedbackSizingDecision holdStable(int corePoolSize, String reason) {
        return new FeedbackSizingDecision(
                corePoolSize,
                queueByCore(corePoolSize),
                ScalingAction.HOLD_STABLE,
                reason
        );
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
