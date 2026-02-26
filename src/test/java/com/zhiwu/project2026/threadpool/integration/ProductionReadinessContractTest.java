package com.zhiwu.project2026.threadpool.integration;

import com.zhiwu.project2026.threadpool.gating.ScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateVerdict;
import com.zhiwu.project2026.threadpool.gating.PressureLevel;
import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControlState;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControllerConfig;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackMetrics;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackSizingController;
import com.zhiwu.project2026.threadpool.schemeb.ScalingAction;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetCalculator;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetInput;
import com.zhiwu.project2026.threadpool.schemec.HybridControlState;
import com.zhiwu.project2026.threadpool.schemec.HybridMetrics;
import com.zhiwu.project2026.threadpool.schemec.HybridScalingAction;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingConfig;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingController;
import com.zhiwu.project2026.threadpool.schemec.HybridThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemed.LinearModelInferenceEngine;
import com.zhiwu.project2026.threadpool.schemed.ModelBasedSizingController;
import com.zhiwu.project2026.threadpool.schemed.ModelBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemed.ModelControlState;
import com.zhiwu.project2026.threadpool.schemed.ModelFeatures;
import com.zhiwu.project2026.threadpool.schemed.ModelScalingAction;
import com.zhiwu.project2026.threadpool.schemed.ModelSizingConfig;
import com.zhiwu.project2026.threadpool.schemed.OfflineModelProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductionReadinessContractTest {

    private static final ScaleUpGate ALWAYS_BLOCK_GATE =
            (ScaleUpGateInput input, ScaleUpGateState state) ->
                    new ScaleUpGateVerdict(false, PressureLevel.NORMAL, "blocked for contract test");

    @Test
    void shouldAllowInjectingUnifiedGateToBlockScaleUpInSchemeB() {
        FeedbackControllerConfig config = new FeedbackControllerConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        FeedbackSizingController controller = new FeedbackSizingController(config, ALWAYS_BLOCK_GATE);
        FeedbackControlState state = new FeedbackControlState();
        FeedbackMetrics highQueue = new FeedbackMetrics(130, 220, 0.92, 0.01, 0.60, 80);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        controller.decide(20, highQueue, state, now);
        controller.decide(20, highQueue, state, now.plusSeconds(30));
        var decision = controller.decide(20, highQueue, state, now.plusSeconds(60));

        assertEquals(ScalingAction.HOLD_STABLE, decision.action());
        assertEquals(20, decision.targetCorePoolSize());
    }

    @Test
    void shouldAllowInjectingUnifiedGateToBlockScaleUpInSchemeC() {
        HybridSizingConfig config = new HybridSizingConfig(
                2, 64, 100, 20_000,
                0.25, 0.12, 2.0, 0.35,
                20, 400, 4, 0.4,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        HybridSizingController controller = new HybridSizingController(config, ALWAYS_BLOCK_GATE);
        HybridControlState state = new HybridControlState();
        HybridMetrics highQueue = new HybridMetrics(130, 220, 0.92, 0.01, 0.60, 80);
        var budget = new com.zhiwu.project2026.threadpool.schemec.HybridBudgetPlan(5, 16, 100, 1600);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        controller.decide(12, budget, highQueue, state, now);
        controller.decide(12, budget, highQueue, state, now.plusSeconds(30));
        var decision = controller.decide(12, budget, highQueue, state, now.plusSeconds(60));

        assertEquals(HybridScalingAction.HOLD_STABLE, decision.action());
        assertEquals(12, decision.targetCorePoolSize());
    }

    @Test
    void shouldAllowInjectingUnifiedGateToBlockScaleUpInSchemeD() {
        ModelSizingConfig config = new ModelSizingConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75,
                0.1, 0.82, 200, Duration.ofMinutes(2)
        );
        OfflineModelProfile profile = new OfflineModelProfile(
                2.0, 0.02, 0.01, 0.005, 10.0, 0.5, 0.2, 4.0,
                100.0, 1.5, 2.0, 0.5, 300.0, 50.0
        );
        ModelBasedSizingController controller = new ModelBasedSizingController(
                config,
                new LinearModelInferenceEngine(profile),
                ALWAYS_BLOCK_GATE
        );
        ModelControlState state = new ModelControlState();
        ModelFeatures highLoad = new ModelFeatures(
                200.0, 130, 220, 0.92, 0.01, 0.60, 80,
                8L * 1024 * 1024 * 1024, 4, 8
        );
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        controller.decide(10, highLoad, state, now);
        controller.decide(10, highLoad, state, now.plusSeconds(30));
        var decision = controller.decide(10, highLoad, state, now.plusSeconds(60));

        assertEquals(ModelScalingAction.HOLD_STABLE, decision.action());
        assertEquals(10, decision.targetCorePoolSize());
    }

    @Test
    void shouldKeepSchemeBCDCompatibleWithFixedLinkedBlockingQueue() {
        LinkedBlockingQueue<Runnable> fixedQueue = new LinkedBlockingQueue<>(500);
        QueueCapacityController queueController = new LinkedBlockingQueueCapacityController(fixedQueue);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        FeedbackControllerConfig bConfig = new FeedbackControllerConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        FeedbackBasedThreadPoolManager bManager = new FeedbackBasedThreadPoolManager(new FeedbackSizingController(bConfig));
        ThreadPoolExecutor bExecutor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, fixedQueue);
        var bResult = bManager.reconcile(
                bExecutor,
                queueController,
                new FeedbackMetrics(120, 200, 0.9, 0.01, 0.6, 80),
                new FeedbackControlState(),
                now
        );
        assertEquals(500, bResult.appliedQueueCapacity());
        assertFalse(bResult.queueShrinkDeferred());

        HybridSizingConfig cConfig = new HybridSizingConfig(
                2, 64, 100, 20_000,
                0.25, 0.12, 2.0, 0.35,
                20, 400, 4, 0.4,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        HybridThreadPoolManager cManager = new HybridThreadPoolManager(
                new HybridBudgetCalculator(cConfig),
                new HybridSizingController(cConfig)
        );
        ThreadPoolExecutor cExecutor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, fixedQueue);
        var cResult = cManager.reconcile(
                cExecutor,
                queueController,
                new HybridBudgetInput(8L * 1024 * 1024 * 1024, 4, 4L * 1024 * 1024, 32L * 1024 * 1024, 8),
                new HybridMetrics(120, 200, 0.9, 0.01, 0.6, 80),
                new HybridControlState(),
                now
        );
        assertEquals(500, cResult.appliedQueueCapacity());
        assertFalse(cResult.queueShrinkDeferred());

        ModelSizingConfig dConfig = new ModelSizingConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75,
                0.1, 0.82, 200, Duration.ofMinutes(2)
        );
        OfflineModelProfile profile = new OfflineModelProfile(
                2.0, 0.02, 0.01, 0.005, 10.0, 0.5, 0.2, 4.0,
                100.0, 1.5, 2.0, 0.5, 300.0, 50.0
        );
        ModelBasedThreadPoolManager dManager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(dConfig, new LinearModelInferenceEngine(profile))
        );
        ThreadPoolExecutor dExecutor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, fixedQueue);
        var dResult = dManager.reconcile(
                dExecutor,
                queueController,
                new ModelFeatures(200.0, 120, 220, 0.9, 0.01, 0.6, 80, 8L * 1024 * 1024 * 1024, 4, 8),
                new ModelControlState(),
                now
        );
        assertEquals(500, dResult.appliedQueueCapacity());
        assertFalse(dResult.queueShrinkDeferred());
    }
}
