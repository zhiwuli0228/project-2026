package com.zhiwu.project2026.threadpool.reliability;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingCalculator;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingConfig;
import com.zhiwu.project2026.threadpool.schemea.SizingInput;
import com.zhiwu.project2026.threadpool.schemea.SizingPlan;
import com.zhiwu.project2026.threadpool.schemeb.AppliedFeedbackResult;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControlState;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControllerConfig;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackMetrics;
import com.zhiwu.project2026.threadpool.schemec.AppliedHybridResult;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetCalculator;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetInput;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetPlan;
import com.zhiwu.project2026.threadpool.schemec.HybridControlState;
import com.zhiwu.project2026.threadpool.schemec.HybridMetrics;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingConfig;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingController;
import com.zhiwu.project2026.threadpool.schemec.HybridThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemed.AppliedModelResult;
import com.zhiwu.project2026.threadpool.schemed.LinearModelInferenceEngine;
import com.zhiwu.project2026.threadpool.schemed.ModelBasedSizingController;
import com.zhiwu.project2026.threadpool.schemed.ModelBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemed.ModelControlState;
import com.zhiwu.project2026.threadpool.schemed.ModelFeatures;
import com.zhiwu.project2026.threadpool.schemed.ModelSizingConfig;
import com.zhiwu.project2026.threadpool.schemed.OfflineModelProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolSafetyReliabilityTest {

    @Test
    void shouldKeepSchemeAStableUnderRandomInputs() {
        RuleBasedSizingConfig config = new RuleBasedSizingConfig(
                2, 64, 2.0, 0.25, 100, 100, 20_000, 4
        );
        RuleBasedSizingCalculator calculator = new RuleBasedSizingCalculator(config);
        Random random = new Random(20260226L);

        for (int i = 0; i < 1000; i++) {
            SizingInput input = new SizingInput(
                    betweenLong(random, 256L * 1024 * 1024, 32L * 1024 * 1024 * 1024),
                    betweenInt(random, 1, 64),
                    betweenLong(random, 1L * 1024 * 1024, 256L * 1024 * 1024),
                    betweenInt(random, 1, 64)
            );
            SizingPlan plan = calculator.calculate(input);

            assertTrue(plan.corePoolSize() >= config.minCore());
            assertTrue(plan.corePoolSize() <= config.maxCore());
            assertTrue(plan.queueCapacity() >= config.minQueue());
            assertTrue(plan.queueCapacity() <= config.maxQueue());
        }
    }

    @Test
    void shouldKeepSchemeBStableInLongRunningReconcileSimulation() {
        FeedbackControllerConfig config = new FeedbackControllerConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75, 0.82, 200,
                0.1, Duration.ofMinutes(2)
        );
        FeedbackBasedThreadPoolManager manager =
                new FeedbackBasedThreadPoolManager(new com.zhiwu.project2026.threadpool.schemeb.FeedbackSizingController(config));
        FeedbackControlState state = new FeedbackControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, queue);
        Random random = new Random(2026022601L);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 500; i++) {
            mutateQueue(queue, random);
            FeedbackMetrics metrics = new FeedbackMetrics(
                    betweenInt(random, 0, 300),
                    betweenInt(random, 1, 500),
                    betweenDouble(random, 0.1, 1.0),
                    betweenDouble(random, 0.0, 0.2),
                    betweenDouble(random, 0.4, 0.95),
                    betweenInt(random, 0, 400)
            );
            AppliedFeedbackResult result = manager.reconcile(executor, queue, metrics, state, now);

            assertEquals(executor.getCorePoolSize(), executor.getMaximumPoolSize());
            assertTrue(result.decision().targetCorePoolSize() >= config.minCore());
            assertTrue(result.decision().targetCorePoolSize() <= config.maxCore());
            assertTrue(result.decision().targetQueueCapacity() >= config.minQueue());
            assertTrue(result.decision().targetQueueCapacity() <= config.maxQueue());
            assertTrue(result.appliedQueueCapacity() >= queue.size());
            assertTrue(result.appliedQueueCapacity() >= result.decision().targetQueueCapacity());
            if (!result.queueShrinkDeferred()) {
                assertEquals(result.decision().targetQueueCapacity(), result.appliedQueueCapacity());
            } else {
                assertTrue(result.appliedQueueCapacity() > result.decision().targetQueueCapacity());
            }

            now = now.plusSeconds(30);
        }
    }

    @Test
    void shouldKeepSchemeCStableInLongRunningReconcileSimulation() {
        HybridSizingConfig config = new HybridSizingConfig(
                2, 64, 100, 20_000,
                0.25, 0.12, 2.0, 0.35,
                20, 400,
                4, 0.4,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        HybridBudgetCalculator budgetCalculator = new HybridBudgetCalculator(config);
        HybridThreadPoolManager manager = new HybridThreadPoolManager(
                budgetCalculator, new HybridSizingController(config)
        );
        HybridControlState state = new HybridControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, queue);
        Random random = new Random(2026022602L);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 500; i++) {
            mutateQueue(queue, random);
            HybridBudgetInput input = new HybridBudgetInput(
                    betweenLong(random, 256L * 1024 * 1024, 32L * 1024 * 1024 * 1024),
                    betweenInt(random, 1, 64),
                    betweenLong(random, 1L * 1024 * 1024, 256L * 1024 * 1024),
                    betweenLong(random, 2L * 1024 * 1024, 512L * 1024 * 1024),
                    betweenInt(random, 1, 64)
            );
            HybridBudgetPlan budget = budgetCalculator.calculate(input);
            HybridMetrics metrics = new HybridMetrics(
                    betweenInt(random, 0, 300),
                    betweenInt(random, 1, 500),
                    betweenDouble(random, 0.1, 1.0),
                    betweenDouble(random, 0.0, 0.2),
                    betweenDouble(random, 0.4, 0.95),
                    betweenInt(random, 0, 400)
            );

            AppliedHybridResult result = manager.reconcile(executor, queue, input, metrics, state, now);

            assertEquals(executor.getCorePoolSize(), executor.getMaximumPoolSize());
            assertTrue(result.decision().targetCorePoolSize() >= budget.coreMin());
            assertTrue(result.decision().targetCorePoolSize() <= budget.coreMax());
            assertTrue(result.decision().targetQueueCapacity() >= budget.queueMin());
            assertTrue(result.decision().targetQueueCapacity() <= budget.queueMax());
            assertTrue(result.appliedQueueCapacity() >= queue.size());
            assertTrue(result.appliedQueueCapacity() >= result.decision().targetQueueCapacity());

            now = now.plusSeconds(30);
        }
    }

    @Test
    void shouldKeepSchemeDStableInLongRunningReconcileSimulation() {
        ModelSizingConfig config = new ModelSizingConfig(
                2, 64, 100, 20_000, 100, 100, 0.85, 3, 0.75, 0.1, 0.82, 200, Duration.ofMinutes(2)
        );
        OfflineModelProfile profile = new OfflineModelProfile(
                2.0, 0.02, 0.01, 0.005, 10.0, 0.5, 0.2, 4.0,
                100.0, 1.5, 2.0, 0.5, 300.0, 50.0
        );
        ModelBasedThreadPoolManager manager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile))
        );
        ModelControlState state = new ModelControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, queue);
        Random random = new Random(2026022603L);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 500; i++) {
            mutateQueue(queue, random);
            ModelFeatures features = new ModelFeatures(
                    betweenDouble(random, 0, 500),
                    betweenInt(random, 0, 300),
                    betweenInt(random, 1, 500),
                    betweenDouble(random, 0.1, 1.0),
                    betweenDouble(random, 0.0, 0.3),
                    betweenDouble(random, 0.2, 0.95),
                    betweenInt(random, 0, 400),
                    betweenLong(random, 256L * 1024 * 1024, 32L * 1024 * 1024 * 1024),
                    betweenInt(random, 1, 64),
                    betweenInt(random, 1, 64)
            );

            AppliedModelResult result = manager.reconcile(executor, queue, features, state, now);

            assertEquals(executor.getCorePoolSize(), executor.getMaximumPoolSize());
            assertTrue(result.decision().targetCorePoolSize() >= config.minCore());
            assertTrue(result.decision().targetCorePoolSize() <= config.maxCore());
            assertTrue(result.decision().targetQueueCapacity() >= config.minQueue());
            assertTrue(result.decision().targetQueueCapacity() <= config.maxQueue());
            assertTrue(result.appliedQueueCapacity() >= queue.size());
            assertTrue(result.appliedQueueCapacity() >= result.decision().targetQueueCapacity());

            now = now.plusSeconds(30);
        }
    }

    @Test
    void shouldKeepResizableQueueSafeUnderConcurrentResizeProduceConsume() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ResizableCapacityBlockingQueue<Integer> queue = new ResizableCapacityBlockingQueue<>(50);
            ExecutorService pool = Executors.newFixedThreadPool(7);
            AtomicInteger produced = new AtomicInteger();
            AtomicInteger consumed = new AtomicInteger();
            int producers = 3;
            int consumers = 3;
            int eachProducer = 200;
            int eachConsumer = (producers * eachProducer) / consumers;
            Random random = new Random(2026022604L);
            List<Future<?>> futures = new ArrayList<>();

            for (int p = 0; p < producers; p++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < eachProducer; i++) {
                        try {
                            queue.put(i);
                            produced.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            fail("producer interrupted");
                        }
                    }
                }));
            }

            for (int c = 0; c < consumers; c++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < eachConsumer; i++) {
                        try {
                            Integer value = queue.take();
                            assertNotNull(value);
                            consumed.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            fail("consumer interrupted");
                        }
                    }
                }));
            }

            futures.add(pool.submit(() -> {
                while (consumed.get() < producers * eachProducer) {
                    int currentSize = queue.size();
                    int desired = betweenInt(random, 20, 80);
                    int safeCapacity = Math.max(currentSize + 1, desired);
                    try {
                        queue.setCapacity(safeCapacity);
                    } catch (IllegalArgumentException ignored) {
                        // Queue size may grow between size() and setCapacity() under contention.
                    }
                }
            }));

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            pool.shutdownNow();

            assertEquals(producers * eachProducer, produced.get());
            assertEquals(producers * eachProducer, consumed.get());
            assertEquals(0, queue.size());
        });
    }

    private void mutateQueue(ResizableCapacityBlockingQueue<Runnable> queue, Random random) {
        if (random.nextDouble() < 0.4 && queue.remainingCapacity() > 0) {
            queue.offer(() -> { });
        }
        if (random.nextDouble() < 0.3 && !queue.isEmpty()) {
            queue.poll();
        }
    }

    private int betweenInt(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long betweenLong(Random random, long min, long max) {
        long bound = max - min + 1;
        long value = Math.abs(random.nextLong()) % bound;
        return min + value;
    }

    private double betweenDouble(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
