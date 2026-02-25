package com.zhiwu.project2026.threadpool.schemec;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridThreadPoolManagerTest {

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

    @Test
    void shouldApplyHybridScaleUpDecision() {
        HybridThreadPoolManager manager = new HybridThreadPoolManager(
                new HybridBudgetCalculator(config),
                new HybridSizingController(config)
        );
        HybridControlState state = new HybridControlState();

        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(12, 12, 60, TimeUnit.SECONDS, queue);

        HybridBudgetInput input = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024,
                4,
                4L * 1024 * 1024,
                32L * 1024 * 1024,
                8
        );
        HybridMetrics highQueue = new HybridMetrics(130, 220, 0.92, 0.0, 0.60, 80);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        manager.reconcile(executor, queue, input, highQueue, state, now);
        manager.reconcile(executor, queue, input, highQueue, state, now.plusSeconds(30));
        AppliedHybridResult result = manager.reconcile(executor, queue, input, highQueue, state, now.plusSeconds(60));

        assertEquals(13, executor.getCorePoolSize());
        assertEquals(13, executor.getMaximumPoolSize());
        assertEquals(HybridScalingAction.SCALE_UP, result.decision().action());
        assertEquals(1600, queue.getCapacity());
        assertFalse(result.queueShrinkDeferred());
    }

    @Test
    void shouldDeferQueueShrinkWhenCurrentQueueSizeExceedsTarget() {
        HybridThreadPoolManager manager = new HybridThreadPoolManager(
                new HybridBudgetCalculator(config),
                new HybridSizingController(config)
        );
        HybridControlState state = new HybridControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(300);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, queue);
        for (int i = 0; i < 250; i++) {
            queue.add(() -> {
            });
        }

        HybridBudgetInput input = new HybridBudgetInput(
                512L * 1024 * 1024,
                16,
                32L * 1024 * 1024,
                128L * 1024 * 1024,
                2
        );
        HybridMetrics heapHigh = new HybridMetrics(20, 100, 0.20, 0.0, 0.90, 50);

        AppliedHybridResult result = manager.reconcile(
                executor,
                queue,
                input,
                heapHigh,
                state,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals(2, result.decision().targetCorePoolSize());
        assertEquals(100, result.decision().targetQueueCapacity());
        assertEquals(250, result.appliedQueueCapacity());
        assertEquals(250, queue.getCapacity());
        assertTrue(result.queueShrinkDeferred());
    }
}
