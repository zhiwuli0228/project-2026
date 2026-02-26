package com.zhiwu.project2026.threadpool.schemed;

import com.zhiwu.project2026.threadpool.gating.DefaultScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateConfig;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateVerdict;

import java.time.Instant;
import java.util.Objects;

public class ModelBasedSizingController {

    private final ModelSizingConfig config;
    private final ModelInferenceEngine inferenceEngine;
    private final ScaleUpGate scaleUpGate;

    public ModelBasedSizingController(ModelSizingConfig config, ModelInferenceEngine inferenceEngine) {
        this(
                config,
                inferenceEngine,
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

    public ModelBasedSizingController(ModelSizingConfig config,
                                      ModelInferenceEngine inferenceEngine,
                                      ScaleUpGate scaleUpGate) {
        this.config = Objects.requireNonNull(config, "config");
        this.inferenceEngine = Objects.requireNonNull(inferenceEngine, "inferenceEngine");
        this.scaleUpGate = Objects.requireNonNull(scaleUpGate, "scaleUpGate");
    }

    public ModelSizingDecision decide(
            int currentCorePoolSize,
            ModelFeatures features,
            ModelControlState state,
            Instant now
    ) {
        if (currentCorePoolSize <= 0) {
            throw new IllegalArgumentException("currentCorePoolSize must be positive");
        }
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        int clampedCurrentCore = clamp(config.minCore(), currentCorePoolSize, config.maxCore());
        int step = Math.max(1, (int) Math.floor(clampedCurrentCore * config.maxStepRatio()));

        if (!cooldownPassed(state, now)) {
            return new ModelSizingDecision(
                    clampedCurrentCore,
                    queueByCore(clampedCurrentCore),
                    ModelScalingAction.HOLD_COOLDOWN,
                    "cooldown not passed"
            );
        }

        if (features.heapUsedRatio() > config.heapGuardHigh()
                || features.gcPauseP95Ms() > config.gcPauseP95MsThreshold()) {
            int protectedCore = clamp(config.minCore(), clampedCurrentCore - step, config.maxCore());
            if (protectedCore == clampedCurrentCore) {
                return holdStable(clampedCurrentCore, "downscale blocked at minCore");
            }
            state.setLastAdjustmentAt(now);
            return new ModelSizingDecision(
                    protectedCore,
                    queueByCore(protectedCore),
                    ModelScalingAction.SCALE_DOWN,
                    "heap or gc pressure high"
            );
        }

        ModelRecommendation recommendation = inferenceEngine.infer(features);
        int desiredCore = clamp(config.minCore(), recommendation.recommendedCorePoolSize(), config.maxCore());
        int desiredQueue = clamp(config.minQueue(), recommendation.recommendedQueueCapacity(), config.maxQueue());

        ScaleUpGateVerdict gateVerdict = scaleUpGate.evaluate(
                new ScaleUpGateInput(
                        features.queueWaitP95Ms(),
                        features.activeRatio(),
                        features.heapUsedRatio(),
                        features.gcPauseP95Ms()
                ),
                state.getScaleUpGateState()
        );

        if (desiredCore > clampedCurrentCore) {
            if (!gateVerdict.allowScaleUp()) {
                return holdStable(clampedCurrentCore, "scale-up gate blocked: " + gateVerdict.reason());
            }
            int targetCore = Math.min(desiredCore, clampedCurrentCore + step);
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new ModelSizingDecision(
                    targetCore,
                    queueByCoreOrRecommendation(targetCore, desiredQueue),
                    ModelScalingAction.SCALE_UP,
                    "scale-up gate passed"
            );
        }
        if (desiredCore < clampedCurrentCore) {
            int targetCore = Math.max(desiredCore, clampedCurrentCore - step);
            state.setLastAdjustmentAt(now);
            state.resetQueueWaitBreaches();
            return new ModelSizingDecision(
                    targetCore,
                    queueByCoreOrRecommendation(targetCore, desiredQueue),
                    ModelScalingAction.SCALE_DOWN,
                    "model recommendation scale down"
            );
        }

        int targetQueue = queueByCoreOrRecommendation(clampedCurrentCore, desiredQueue);
        if (targetQueue != queueByCore(clampedCurrentCore)) {
            return new ModelSizingDecision(
                    clampedCurrentCore,
                    targetQueue,
                    ModelScalingAction.HOLD_STABLE,
                    "model queue-only adjustment"
            );
        }
        return holdStable(clampedCurrentCore, "no scaling condition met");
    }

    private boolean cooldownPassed(ModelControlState state, Instant now) {
        Instant lastAdjustmentAt = state.getLastAdjustmentAt();
        return lastAdjustmentAt == null
                || !lastAdjustmentAt.plus(config.cooldown()).isAfter(now);
    }

    private int queueByCore(int corePoolSize) {
        int linkedQueue = saturatedMultiply(corePoolSize, config.queuePerThreadBaseline());
        return clamp(config.minQueue(), linkedQueue, config.maxQueue());
    }

    private int queueByCoreOrRecommendation(int corePoolSize, int recommendedQueue) {
        int linkedQueue = queueByCore(corePoolSize);
        int preferred = Math.max(linkedQueue, recommendedQueue);
        return clamp(config.minQueue(), preferred, config.maxQueue());
    }

    private ModelSizingDecision holdStable(int corePoolSize, String reason) {
        return new ModelSizingDecision(
                corePoolSize,
                queueByCore(corePoolSize),
                ModelScalingAction.HOLD_STABLE,
                reason
        );
    }

    private int clamp(int min, int value, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private int saturatedMultiply(int left, int right) {
        long product = (long) left * right;
        if (product > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (product < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) product;
    }
}
