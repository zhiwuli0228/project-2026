package com.zhiwu.project2026.threadpool.schemea;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}

