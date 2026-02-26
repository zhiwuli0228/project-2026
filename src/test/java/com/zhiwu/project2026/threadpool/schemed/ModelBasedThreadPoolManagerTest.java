package com.zhiwu.project2026.threadpool.schemed;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBasedThreadPoolManagerTest {

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

    @Test
    void shouldApplyModelDecisionToExecutorAndQueue() {
        ModelBasedThreadPoolManager manager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile))
        );
        ModelControlState state = new ModelControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, queue);

        ModelFeatures highLoad = new ModelFeatures(
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

        AppliedModelResult result = manager.reconcile(
                executor,
                queue,
                highLoad,
                state,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        manager.reconcile(executor, queue, highLoad, state, Instant.parse("2026-01-01T00:00:30Z"));
        result = manager.reconcile(executor, queue, highLoad, state, Instant.parse("2026-01-01T00:01:00Z"));

        assertEquals(11, executor.getCorePoolSize());
        assertEquals(11, executor.getMaximumPoolSize());
        assertEquals(1496, queue.getCapacity());
        assertEquals(1496, result.appliedQueueCapacity());
        assertFalse(result.queueShrinkDeferred());
        assertEquals(ModelScalingAction.SCALE_UP, result.decision().action());
    }

    @Test
    void shouldDeferQueueShrinkIfCurrentQueueSizeExceedsTarget() {
        ModelBasedThreadPoolManager manager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile))
        );
        ModelControlState state = new ModelControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(300);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);
        for (int i = 0; i < 250; i++) {
            queue.add(() -> { });
        }
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

        AppliedModelResult result = manager.reconcile(
                executor,
                queue,
                heapHigh,
                state,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals(2, result.decision().targetCorePoolSize());
        assertEquals(200, result.decision().targetQueueCapacity());
        assertEquals(250, result.appliedQueueCapacity());
        assertEquals(250, queue.getCapacity());
        assertTrue(result.queueShrinkDeferred());
    }

    @Test
    void shouldRejectNullArguments() {
        ModelBasedThreadPoolManager manager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile))
        );
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(10);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, queue);
        ModelFeatures features = new ModelFeatures(1.0, 1, 1, 0.5, 0.0, 0.1, 1, 1024, 1, 1);
        ModelControlState state = new ModelControlState();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(NullPointerException.class, () -> manager.reconcile(null, queue, features, state, now));
        assertThrows(NullPointerException.class,
                () -> manager.reconcile(executor, (ResizableCapacityBlockingQueue<Runnable>) null, features, state, now));
        assertThrows(NullPointerException.class, () -> manager.reconcile(executor, queue, null, state, now));
        assertThrows(NullPointerException.class, () -> manager.reconcile(executor, queue, features, null, now));
        assertThrows(NullPointerException.class, () -> manager.reconcile(executor, queue, features, state, null));
        assertThrows(NullPointerException.class, () -> new ModelBasedThreadPoolManager(null));
    }
}

