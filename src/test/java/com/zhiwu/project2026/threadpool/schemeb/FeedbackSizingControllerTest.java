package com.zhiwu.project2026.threadpool.schemeb;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackSizingControllerTest {

    private final FeedbackControllerConfig config = new FeedbackControllerConfig(
            2,
            64,
            100,
            20_000,
            100,
            100,
            0.85,
            3,
            0.75,
            0.82,
            200,
            0.1,
            Duration.ofMinutes(2)
    );

    private final FeedbackSizingController controller = new FeedbackSizingController(config);

    @Test
    void shouldScaleUpAfterConsecutiveQueueBreaches() {
        FeedbackControlState state = new FeedbackControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackMetrics highQueue = new FeedbackMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        controller.decide(20, highQueue, state, now);
        controller.decide(20, highQueue, state, now.plusSeconds(30));
        FeedbackSizingDecision decision = controller.decide(20, highQueue, state, now.plusSeconds(60));

        assertEquals(ScalingAction.SCALE_UP, decision.action());
        assertEquals(22, decision.targetCorePoolSize());
        assertEquals(2200, decision.targetQueueCapacity());
    }

    @Test
    void shouldScaleDownWhenHeapOrGcCrossesGuard() {
        FeedbackControlState state = new FeedbackControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackMetrics heapHigh = new FeedbackMetrics(30, 120, 0.4, 0.0, 0.90, 50);

        FeedbackSizingDecision decision = controller.decide(20, heapHigh, state, now);

        assertEquals(ScalingAction.SCALE_DOWN, decision.action());
        assertEquals(18, decision.targetCorePoolSize());
        assertEquals(1800, decision.targetQueueCapacity());
    }

    @Test
    void shouldHoldDuringCooldownWindow() {
        FeedbackControlState state = new FeedbackControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackMetrics heapHigh = new FeedbackMetrics(30, 120, 0.4, 0.0, 0.90, 50);
        FeedbackMetrics highQueue = new FeedbackMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        controller.decide(20, heapHigh, state, now);
        FeedbackSizingDecision cooldownDecision = controller.decide(18, highQueue, state, now.plusSeconds(30));

        assertEquals(ScalingAction.HOLD_COOLDOWN, cooldownDecision.action());
        assertEquals(18, cooldownDecision.targetCorePoolSize());
        assertEquals(1800, cooldownDecision.targetQueueCapacity());
    }
}
