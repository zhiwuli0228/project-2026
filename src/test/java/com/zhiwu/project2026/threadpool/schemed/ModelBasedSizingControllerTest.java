package com.zhiwu.project2026.threadpool.schemed;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelBasedSizingControllerTest {

    private final ModelSizingConfig config = new ModelSizingConfig(
            2,
            64,
            100,
            20_000,
            100,
            100,
            0.85,
            3,
            0.75,
            0.1,
            0.82,
            200,
            Duration.ofMinutes(2)
    );

    private final OfflineModelProfile profile = new OfflineModelProfile(
            2.0,
            0.02,
            0.01,
            0.005,
            10.0,
            0.5,
            0.2,
            4.0,
            100.0,
            1.5,
            2.0,
            0.5,
            300.0,
            50.0
    );

    private final ModelBasedSizingController controller =
            new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile));

    @Test
    void shouldScaleUpFollowingModelRecommendationWithStepLimit() {
        ModelControlState state = new ModelControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        controller.decide(10, highLoadFeatures(), state, now);
        controller.decide(10, highLoadFeatures(), state, now.plusSeconds(30));
        ModelSizingDecision decision = controller.decide(10, highLoadFeatures(), state, now.plusSeconds(60));

        assertEquals(ModelScalingAction.SCALE_UP, decision.action());
        assertEquals(11, decision.targetCorePoolSize());
        assertEquals(1496, decision.targetQueueCapacity());
    }

    @Test
    void shouldHoldDuringCooldown() {
        ModelControlState state = new ModelControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        controller.decide(10, highLoadFeatures(), state, now);
        controller.decide(10, highLoadFeatures(), state, now.plusSeconds(30));
        controller.decide(10, highLoadFeatures(), state, now.plusSeconds(60));
        ModelSizingDecision cooldown = controller.decide(11, highLoadFeatures(), state, now.plusSeconds(90));

        assertEquals(ModelScalingAction.HOLD_COOLDOWN, cooldown.action());
        assertEquals(11, cooldown.targetCorePoolSize());
        assertEquals(1100, cooldown.targetQueueCapacity());
    }

    @Test
    void shouldForceScaleDownOnHeapOrGcPressure() {
        ModelControlState state = new ModelControlState();
        ModelFeatures heapHigh = new ModelFeatures(
                200.0,
                120,
                200,
                0.90,
                0.02,
                0.90,
                80,
                8L * 1024 * 1024 * 1024,
                4,
                8
        );

        ModelSizingDecision decision = controller.decide(
                10,
                heapHigh,
                state,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals(ModelScalingAction.SCALE_DOWN, decision.action());
        assertEquals(9, decision.targetCorePoolSize());
        assertEquals(900, decision.targetQueueCapacity());
    }

    @Test
    void shouldNotEnterCooldownWhenDownscaleBlockedAtMinCore() {
        ModelControlState state = new ModelControlState();
        ModelFeatures heapHigh = new ModelFeatures(
                20.0,
                20,
                80,
                0.40,
                0.0,
                0.90,
                80,
                2L * 1024 * 1024 * 1024,
                8,
                2
        );
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        ModelSizingDecision blocked = controller.decide(2, heapHigh, state, now);
        ModelSizingDecision next = controller.decide(2, heapHigh, state, now.plusSeconds(30));

        assertEquals(ModelScalingAction.HOLD_STABLE, blocked.action());
        assertEquals(ModelScalingAction.HOLD_STABLE, next.action());
    }

    private ModelFeatures highLoadFeatures() {
        return new ModelFeatures(
                200.0,
                120,
                200,
                0.90,
                0.02,
                0.60,
                80,
                8L * 1024 * 1024 * 1024,
                4,
                8
        );
    }
}

