package com.zhiwu.project2026.threadpool.gating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultScaleUpGateTest {

    private final DefaultScaleUpGate gate = new DefaultScaleUpGate(
            new ScaleUpGateConfig(100, 0.85, 0.75, 200, 3)
    );

    @Test
    void shouldRequireConsecutiveBreachesBeforeAllowingScaleUp() {
        ScaleUpGateState state = new ScaleUpGateState();
        ScaleUpGateInput highPressure = new ScaleUpGateInput(130, 0.9, 0.6, 80);

        assertFalse(gate.evaluate(highPressure, state).allowScaleUp());
        assertFalse(gate.evaluate(highPressure, state).allowScaleUp());
        assertTrue(gate.evaluate(highPressure, state).allowScaleUp());
    }

    @Test
    void shouldResetConsecutiveBreachesWhenQueueWaitRecovers() {
        ScaleUpGateState state = new ScaleUpGateState();
        ScaleUpGateInput highPressure = new ScaleUpGateInput(130, 0.9, 0.6, 80);
        ScaleUpGateInput recovered = new ScaleUpGateInput(80, 0.9, 0.6, 80);

        gate.evaluate(highPressure, state);
        gate.evaluate(highPressure, state);
        gate.evaluate(recovered, state);

        assertFalse(gate.evaluate(highPressure, state).allowScaleUp());
    }
}
