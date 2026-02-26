package com.zhiwu.project2026.threadpool.schemed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinearModelInferenceEngineTest {

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

    @Test
    void shouldInferCoreAndQueueFromLinearProfile() {
        LinearModelInferenceEngine engine = new LinearModelInferenceEngine(profile);
        ModelFeatures features = new ModelFeatures(
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

        ModelRecommendation recommendation = engine.infer(features);

        assertEquals(15, recommendation.recommendedCorePoolSize());
        assertEquals(1496, recommendation.recommendedQueueCapacity());
    }

    @Test
    void shouldRejectNullInput() {
        LinearModelInferenceEngine engine = new LinearModelInferenceEngine(profile);
        assertThrows(NullPointerException.class, () -> engine.infer(null));
        assertThrows(NullPointerException.class, () -> new LinearModelInferenceEngine(null));
    }
}

