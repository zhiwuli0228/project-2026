package com.zhiwu.project2026.threadpool.schemec;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HybridBudgetCalculatorTest {

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

    private final HybridBudgetCalculator calculator = new HybridBudgetCalculator(config);

    @Test
    void shouldCalculateBudgetByMemCpuAndReplica() {
        HybridBudgetInput input = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024,
                4,
                4L * 1024 * 1024,
                32L * 1024 * 1024,
                8
        );

        HybridBudgetPlan plan = calculator.calculate(input);

        assertEquals(5, plan.coreMin());
        assertEquals(16, plan.coreMax());
        assertEquals(100, plan.queueMin());
        assertEquals(1600, plan.queueMax());
    }

    @Test
    void shouldScaleDownBudgetWhenReplicaIncreases() {
        HybridBudgetInput lowReplica = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024,
                4,
                4L * 1024 * 1024,
                32L * 1024 * 1024,
                8
        );
        HybridBudgetInput highReplica = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024,
                16,
                4L * 1024 * 1024,
                32L * 1024 * 1024,
                8
        );

        HybridBudgetPlan low = calculator.calculate(lowReplica);
        HybridBudgetPlan high = calculator.calculate(highReplica);

        assertEquals(16, low.coreMax());
        assertEquals(9, high.coreMax());
        assertEquals(5, low.coreMin());
        assertEquals(3, high.coreMin());
    }
}
