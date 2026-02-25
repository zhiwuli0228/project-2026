package com.zhiwu.project2026.threadpool.schemeb;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackBasedThreadPoolManagerTest {

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

    @Test
    void shouldApplyScaleUpDecisionToExecutorAndQueue() {
        FeedbackSizingController controller = new FeedbackSizingController(config);
        FeedbackBasedThreadPoolManager manager = new FeedbackBasedThreadPoolManager(controller);
        FeedbackControlState state = new FeedbackControlState();

        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(20, 20, 60, TimeUnit.SECONDS, queue);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackMetrics highQueue = new FeedbackMetrics(130, 220, 0.92, 0.0, 0.60, 80);
        manager.reconcile(executor, queue, highQueue, state, now);
        manager.reconcile(executor, queue, highQueue, state, now.plusSeconds(30));
        AppliedFeedbackResult result = manager.reconcile(executor, queue, highQueue, state, now.plusSeconds(60));

        assertEquals(22, executor.getCorePoolSize());
        assertEquals(22, executor.getMaximumPoolSize());
        assertEquals(2200, queue.getCapacity());
        assertEquals(2200, result.appliedQueueCapacity());
        assertFalse(result.queueShrinkDeferred());
        assertEquals(ScalingAction.SCALE_UP, result.decision().action());
    }

    @Test
    void shouldDeferQueueShrinkIfCurrentQueueSizeExceedsTarget() {
        FeedbackSizingController controller = new FeedbackSizingController(config);
        FeedbackBasedThreadPoolManager manager = new FeedbackBasedThreadPoolManager(controller);
        FeedbackControlState state = new FeedbackControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(300);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);

        for (int i = 0; i < 250; i++) {
            queue.add(() -> {
            });
        }

        FeedbackMetrics heapHigh = new FeedbackMetrics(20, 100, 0.20, 0.0, 0.90, 50);
        AppliedFeedbackResult result = manager.reconcile(
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
}
