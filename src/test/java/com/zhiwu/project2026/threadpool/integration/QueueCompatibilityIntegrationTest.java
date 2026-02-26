package com.zhiwu.project2026.threadpool.integration;

import com.zhiwu.project2026.threadpool.schemea.AppliedSizingResult;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingCalculator;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingConfig;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemea.SizingInput;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QueueCompatibilityIntegrationTest {

    @Test
    void shouldWorkWithLinkedBlockingQueueControllerWithoutResize() {
        RuleBasedSizingConfig config = new RuleBasedSizingConfig(
                2, 64, 2.0, 0.25, 100, 100, 20_000, 4
        );
        RuleBasedThreadPoolManager manager =
                new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(500);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, queue);
        QueueCapacityController controller = new LinkedBlockingQueueCapacityController(queue);
        SizingInput input = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );

        AppliedSizingResult result = manager.reconcile(executor, controller, input, null, null);

        assertEquals(16, executor.getCorePoolSize());
        assertEquals(16, executor.getMaximumPoolSize());
        assertEquals(500, result.planned().queueCapacity());
        assertEquals(500, result.appliedQueueCapacity());
        assertFalse(result.queueShrinkDeferred());
    }
}
