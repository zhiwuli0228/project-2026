package com.zhiwu.project2026.threadpool.schemec;

import com.zhiwu.project2026.threadpool.gating.DefaultScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateConfig;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateVerdict;

import java.time.Instant;
import java.util.Objects;

public class HybridSizingController {

    private final HybridSizingConfig config;
    private final ScaleUpGate scaleUpGate;

    public HybridSizingController(HybridSizingConfig config) {
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

    public HybridSizingController(HybridSizingConfig config, ScaleUpGate scaleUpGate) {
        this.config = Objects.requireNonNull(config, "config");
        this.scaleUpGate = Objects.requireNonNull(scaleUpGate, "scaleUpGate");
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
            if (targetCore == clampedCurrentCore) {
                return holdStable(clampedCurrentCore, budget, "downscale blocked at coreMin");
            }
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new HybridSizingDecision(
                    targetCore,
                    queueByCoreWithinBudget(targetCore, budget),
                    HybridScalingAction.SCALE_DOWN,
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
            int targetCore = clamp(budget.coreMin(), clampedCurrentCore + step, budget.coreMax());
            if (targetCore == clampedCurrentCore) {
                return holdStable(clampedCurrentCore, budget, "upscale blocked at coreMax");
            }
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new HybridSizingDecision(
                    targetCore,
                    queueByCoreWithinBudget(targetCore, budget),
                    HybridScalingAction.SCALE_UP,
                    "scale-up gate passed"
            );
        }

        return holdStable(clampedCurrentCore, budget, "scale-up gate blocked: " + gateVerdict.reason());
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

    private HybridSizingDecision holdStable(int corePoolSize, HybridBudgetPlan budget, String reason) {
        return new HybridSizingDecision(
                corePoolSize,
                queueByCoreWithinBudget(corePoolSize, budget),
                HybridScalingAction.HOLD_STABLE,
                reason
        );
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
