package com.zhiwu.project2026.threadpool.schemed;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemeDValidationTest {

    @Test
    void shouldValidateConfigAndFeatureInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(0, 2, 1, 2, 1, 100, 0.85, 3, 0.75, 0.1, 0.8, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 0, 2, 1, 100, 0.85, 3, 0.75, 0.1, 0.8, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 1, 2, 1, 100, 0.85, 3, 0.75, 0.0, 0.8, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 1, 2, 1, 100, 0.85, 3, 0.75, 0.1, 1.0, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 1, 2, 1, 100, 0.85, 3, 0.75, 0.1, 0.8, 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 1, 2, 1, 100, 0.85, 3, 0.75, 0.1, 0.8, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingConfig(1, 2, 1, 2, 1, 100, 0.85, 3, 0.9, 0.1, 0.8, 1, Duration.ofSeconds(1)));

        assertThrows(IllegalArgumentException.class,
                () -> new ModelFeatures(-1, 1, 1, 0.5, 0.0, 0.1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelFeatures(1, -1, 1, 0.5, 0.0, 0.1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelFeatures(1, 1, 1, 1.2, 0.0, 0.1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelFeatures(1, 1, 1, 0.2, 0.2, 1.2, 1, 1, 1, 1));
    }

    @Test
    void shouldValidateModelProfileAndOutputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfflineModelProfile(
                        Double.NaN,
                        0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0
                ));

        assertThrows(IllegalArgumentException.class, () -> new ModelRecommendation(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ModelRecommendation(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ModelRecommendation(1, 1, Double.NaN, 1));
    }

    @Test
    void shouldValidateDecisionAndAppliedResultConsistency() {
        ModelSizingDecision decision = new ModelSizingDecision(
                4,
                400,
                ModelScalingAction.HOLD_STABLE,
                "ok"
        );

        assertThrows(IllegalArgumentException.class,
                () -> new ModelSizingDecision(0, 1, ModelScalingAction.SCALE_UP, "bad"));
        assertThrows(NullPointerException.class,
                () -> new ModelSizingDecision(1, 1, null, "bad"));
        assertThrows(NullPointerException.class,
                () -> new ModelSizingDecision(1, 1, ModelScalingAction.SCALE_UP, null));

        assertThrows(NullPointerException.class, () -> new AppliedModelResult(null, 400, false));
        assertThrows(IllegalArgumentException.class, () -> new AppliedModelResult(decision, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new AppliedModelResult(decision, 400, true));
        assertThrows(IllegalArgumentException.class, () -> new AppliedModelResult(decision, 450, false));
    }
}
