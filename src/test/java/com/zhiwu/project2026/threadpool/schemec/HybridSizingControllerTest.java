package com.zhiwu.project2026.threadpool.schemec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HybridSizingControllerTest {

    private final HybridSizingConfig config = new HybridSizingConfig(
            2,
            64,
            100,
            20_000,
            0.25,
            0.12,
            2.0,
            0.35,
            20,
            400,
            4,
            0.4,
            100,
            0.85,
            3,
            0.75,
            0.82,
            200,
            0.1,
            Duration.ofMinutes(2)
    );

    private final HybridSizingController controller = new HybridSizingController(config);
    private final HybridBudgetPlan budget = new HybridBudgetPlan(5, 16, 100, 1600);

    @Test
    void shouldScaleUpWithinBudgetAfterConsecutiveBreaches() {
        HybridControlState state = new HybridControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        HybridMetrics highQueue = new HybridMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        controller.decide(12, budget, highQueue, state, now);
        controller.decide(12, budget, highQueue, state, now.plusSeconds(30));
        HybridSizingDecision decision = controller.decide(12, budget, highQueue, state, now.plusSeconds(60));

        assertEquals(HybridScalingAction.SCALE_UP, decision.action());
        assertEquals(13, decision.targetCorePoolSize());
        assertEquals(1600, decision.targetQueueCapacity());
    }

    @Test
    void shouldScaleDownWhenHeapHigh() {
        HybridControlState state = new HybridControlState();
        HybridMetrics heapHigh = new HybridMetrics(20, 100, 0.3, 0.0, 0.90, 50);

        HybridSizingDecision decision = controller.decide(
                10,
                budget,
                heapHigh,
                state,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals(HybridScalingAction.SCALE_DOWN, decision.action());
        assertEquals(9, decision.targetCorePoolSize());
        assertEquals(1600, decision.targetQueueCapacity());
    }

    @Test
    void shouldHoldDuringCooldown() {
        HybridControlState state = new HybridControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        HybridMetrics heapHigh = new HybridMetrics(20, 100, 0.3, 0.0, 0.90, 50);
        HybridMetrics highQueue = new HybridMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        controller.decide(12, budget, heapHigh, state, now);
        HybridSizingDecision cooldown = controller.decide(11, budget, highQueue, state, now.plusSeconds(30));

        assertEquals(HybridScalingAction.HOLD_COOLDOWN, cooldown.action());
        assertEquals(11, cooldown.targetCorePoolSize());
        assertEquals(1600, cooldown.targetQueueCapacity());
    }

    @Test
    void shouldNotEnterCooldownWhenUpscaleBlockedAtCoreMax() {
        HybridControlState state = new HybridControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        HybridMetrics highQueue = new HybridMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        controller.decide(16, budget, highQueue, state, now);
        controller.decide(16, budget, highQueue, state, now.plusSeconds(30));
        HybridSizingDecision blocked = controller.decide(16, budget, highQueue, state, now.plusSeconds(60));
        HybridSizingDecision next = controller.decide(16, budget, highQueue, state, now.plusSeconds(90));

        assertEquals(HybridScalingAction.HOLD_STABLE, blocked.action());
        assertEquals(HybridScalingAction.HOLD_STABLE, next.action());
    }

    @Test
    void shouldNotEnterCooldownWhenDownscaleBlockedAtCoreMin() {
        HybridControlState state = new HybridControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        HybridMetrics heapHigh = new HybridMetrics(20, 100, 0.3, 0.0, 0.90, 50);

        HybridSizingDecision blocked = controller.decide(5, budget, heapHigh, state, now);
        HybridSizingDecision next = controller.decide(5, budget, heapHigh, state, now.plusSeconds(30));

        assertEquals(HybridScalingAction.HOLD_STABLE, blocked.action());
        assertEquals(HybridScalingAction.HOLD_STABLE, next.action());
    }
}
