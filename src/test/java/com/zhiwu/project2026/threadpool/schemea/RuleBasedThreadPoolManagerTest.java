package com.zhiwu.project2026.threadpool.schemea;

import com.zhiwu.project2026.threadpool.gating.DefaultScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateConfig;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedThreadPoolManagerTest {

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

    @Test
    void shouldApplyCoreAndQueueCapacity() {
        RuleBasedThreadPoolManager manager =
                new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(500);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);

        SizingInput input = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );

        AppliedSizingResult result = manager.reconcile(executor, queue, input);

        assertEquals(16, executor.getCorePoolSize());
        assertEquals(16, executor.getMaximumPoolSize());
        assertEquals(1600, queue.getCapacity());
        assertEquals(1600, result.appliedQueueCapacity());
        assertFalse(result.queueShrinkDeferred());
    }

    @Test
    void shouldDeferQueueShrinkWhenCurrentQueueSizeIsBiggerThanTarget() {
        RuleBasedThreadPoolManager manager =
                new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(300);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);

        for (int i = 0; i < 250; i++) {
            queue.add(() -> { });
        }

        SizingInput input = new SizingInput(
                128L * 1024 * 1024,
                32,
                64L * 1024 * 1024,
                2
        );

        AppliedSizingResult result = manager.reconcile(executor, queue, input);

        assertEquals(2, result.planned().corePoolSize());
        assertEquals(200, result.planned().queueCapacity());
        assertEquals(250, result.appliedQueueCapacity());
        assertEquals(250, queue.getCapacity());
        assertTrue(result.queueShrinkDeferred());
    }

    @Test
    void shouldRejectNullArguments() {
        RuleBasedThreadPoolManager manager =
                new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(10);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, queue);
        SizingInput input = new SizingInput(1024, 1, 1, 1);

        assertThrows(NullPointerException.class, () -> manager.reconcile(null, queue, input));
        assertThrows(NullPointerException.class,
                () -> manager.reconcile(executor, (ResizableCapacityBlockingQueue<Runnable>) null, input));
        assertThrows(NullPointerException.class, () -> manager.reconcile(executor, queue, null));
        assertThrows(NullPointerException.class, () -> new RuleBasedThreadPoolManager(null));
    }

    @Test
    void shouldBlockScaleUpWhenUnifiedGateNotPassed() {
        RuleBasedThreadPoolManager manager = new RuleBasedThreadPoolManager(
                new RuleBasedSizingCalculator(config),
                new DefaultScaleUpGate(new ScaleUpGateConfig(100, 0.85, 0.75, 200, 3))
        );
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(500);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);
        SizingInput input = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );
        ScaleUpGateState gateState = new ScaleUpGateState();
        ScaleUpGateInput lowPressure = new ScaleUpGateInput(20, 0.3, 0.5, 30);

        AppliedSizingResult result = manager.reconcile(executor, queue, input, lowPressure, gateState);

        assertEquals(2, result.planned().corePoolSize());
        assertEquals(500, result.planned().queueCapacity());
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(500, queue.getCapacity());
    }
}
