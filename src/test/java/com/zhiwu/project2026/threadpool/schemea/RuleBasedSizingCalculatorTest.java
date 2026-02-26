package com.zhiwu.project2026.threadpool.schemea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleBasedSizingCalculatorTest {

    private final RuleBasedSizingConfig config = new RuleBasedSizingConfig(
            2,
            64,
            2.0,
            0.25,
            100,
            100,
            20_000,
            4
    );

    private final RuleBasedSizingCalculator calculator = new RuleBasedSizingCalculator(config);

    @Test
    void shouldCalculateByRuleFormula() {
        SizingInput input = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );

        SizingPlan plan = calculator.calculate(input);

        assertEquals(16, plan.corePoolSize());
        assertEquals(1600, plan.queueCapacity());
    }

    @Test
    void shouldScaleDownWhenReplicaIncreases() {
        SizingInput lowReplica = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );
        SizingInput highReplica = new SizingInput(
                8L * 1024 * 1024 * 1024,
                16,
                32L * 1024 * 1024,
                8
        );

        SizingPlan low = calculator.calculate(lowReplica);
        SizingPlan high = calculator.calculate(highReplica);

        assertEquals(16, low.corePoolSize());
        assertEquals(8, high.corePoolSize());
    }

    @Test
    void shouldClampToMinWhenMemoryVerySmall() {
        SizingInput input = new SizingInput(
                256L * 1024 * 1024,
                4,
                128L * 1024 * 1024,
                8
        );

        SizingPlan plan = calculator.calculate(input);

        assertEquals(2, plan.corePoolSize());
        assertEquals(200, plan.queueCapacity());
    }

    @Test
    void shouldFailOnInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new SizingInput(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SizingInput(1, 0, 1, 1));
    }

    @Test
    void shouldRejectNullConfig() {
        assertThrows(NullPointerException.class, () -> new RuleBasedSizingCalculator(null));
    }

    @Test
    void shouldClampQueueWhenMultiplicationWouldOverflow() {
        RuleBasedSizingConfig overflowProneConfig = new RuleBasedSizingConfig(
                2,
                100_000,
                100_000.0,
                1.0,
                Integer.MAX_VALUE,
                100,
                1_000_000,
                1
        );
        RuleBasedSizingCalculator overflowCalculator = new RuleBasedSizingCalculator(overflowProneConfig);
        SizingInput input = new SizingInput(
                16L * 1024 * 1024 * 1024,
                1,
                1,
                128
        );

        SizingPlan plan = overflowCalculator.calculate(input);

        assertEquals(100_000, plan.corePoolSize());
        assertEquals(1_000_000, plan.queueCapacity());
    }
}
