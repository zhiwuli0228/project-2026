package com.zhiwu.project2026.threadpool.performance;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingCalculator;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedSizingConfig;
import com.zhiwu.project2026.threadpool.schemea.RuleBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemea.SizingInput;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackBasedThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControlState;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackControllerConfig;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackMetrics;
import com.zhiwu.project2026.threadpool.schemeb.FeedbackSizingController;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetCalculator;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetInput;
import com.zhiwu.project2026.threadpool.schemec.HybridControlState;
import com.zhiwu.project2026.threadpool.schemec.HybridMetrics;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingConfig;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingController;
import com.zhiwu.project2026.threadpool.schemec.HybridThreadPoolManager;
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
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolSchemeConcurrentPerformanceTest {

    private static final int THREADS = 8;
    private static final int OPS_PER_THREAD = 10_000;
    private static final double MIN_OPS_PER_SEC = 200_000.0;

    @Test
    void benchmarkSchemeAConcurrentReconcile() throws Exception {
        RuleBasedSizingConfig config = new RuleBasedSizingConfig(
                2, 64, 2.0, 0.25, 100, 100, 20_000, 4
        );
        RuleBasedThreadPoolManager manager = new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));
        SizingInput input = new SizingInput(8L * 1024 * 1024 * 1024, 4, 32L * 1024 * 1024, 8);

        PerfResult result = runConcurrent(() -> {
            ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
            return i -> manager.reconcile(executor, queue, input);
        });
        print("SchemeA-Concurrent", result);
        assertTrue(result.opsPerSec > MIN_OPS_PER_SEC);
    }

    @Test
    void benchmarkSchemeBConcurrentReconcile() throws Exception {
        FeedbackControllerConfig config = new FeedbackControllerConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        FeedbackBasedThreadPoolManager manager = new FeedbackBasedThreadPoolManager(new FeedbackSizingController(config));
        FeedbackMetrics metrics = new FeedbackMetrics(130, 220, 0.92, 0.01, 0.60, 80);

        PerfResult result = runConcurrent(() -> {
            FeedbackControlState state = new FeedbackControlState();
            ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
            return i -> manager.reconcile(executor, queue, metrics, state, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        });
        print("SchemeB-Concurrent", result);
        assertTrue(result.opsPerSec > MIN_OPS_PER_SEC);
    }

    @Test
    void benchmarkSchemeCConcurrentReconcile() throws Exception {
        HybridSizingConfig config = new HybridSizingConfig(
                2, 64, 100, 20_000,
                0.25, 0.12, 2.0, 0.35,
                20, 400, 4, 0.4,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        HybridThreadPoolManager manager = new HybridThreadPoolManager(
                new HybridBudgetCalculator(config),
                new HybridSizingController(config)
        );
        HybridBudgetInput input = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024, 4, 4L * 1024 * 1024, 32L * 1024 * 1024, 8
        );
        HybridMetrics metrics = new HybridMetrics(130, 220, 0.92, 0.01, 0.60, 80);

        PerfResult result = runConcurrent(() -> {
            HybridControlState state = new HybridControlState();
            ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
            return i -> manager.reconcile(executor, queue, input, metrics, state, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        });
        print("SchemeC-Concurrent", result);
        assertTrue(result.opsPerSec > MIN_OPS_PER_SEC);
    }

    @Test
    void benchmarkSchemeDConcurrentReconcile() throws Exception {
        ModelSizingConfig config = new ModelSizingConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75,
                0.1, 0.82, 200, Duration.ofMinutes(2)
        );
        OfflineModelProfile profile = new OfflineModelProfile(
                2.0, 0.02, 0.01, 0.005, 10.0, 0.5, 0.2, 4.0,
                100.0, 1.5, 2.0, 0.5, 300.0, 50.0
        );
        ModelBasedThreadPoolManager manager = new ModelBasedThreadPoolManager(
                new ModelBasedSizingController(config, new LinearModelInferenceEngine(profile))
        );
        ModelFeatures features = new ModelFeatures(
                200.0, 130, 220, 0.92, 0.01, 0.60, 80,
                8L * 1024 * 1024 * 1024, 4, 8
        );

        PerfResult result = runConcurrent(() -> {
            ModelControlState state = new ModelControlState();
            ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
            return i -> manager.reconcile(executor, queue, features, state, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        });
        print("SchemeD-Concurrent", result);
        assertTrue(result.opsPerSec > MIN_OPS_PER_SEC);
    }

    private PerfResult runConcurrent(ThreadActionFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicInteger seq = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        long start = System.nanoTime();
        for (int t = 0; t < THREADS; t++) {
            IntAction action = factory.create();
            futures.add(pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    action.execute(seq.getAndIncrement());
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        long elapsed = System.nanoTime() - start;
        pool.shutdownNow();
        long totalOps = (long) THREADS * OPS_PER_THREAD;
        double avgNs = elapsed / (double) totalOps;
        double opsPerSec = totalOps * 1_000_000_000.0 / elapsed;
        return new PerfResult(elapsed, avgNs, opsPerSec, totalOps);
    }

    private void print(String name, PerfResult result) {
        System.out.println(String.format(
                Locale.ROOT,
                "PERF %s totalOps=%d elapsedMs=%.3f avgNs=%.3f opsPerSec=%.2f",
                name,
                result.totalOps,
                result.elapsedNs / 1_000_000.0,
                result.avgNsPerOp,
                result.opsPerSec
        ));
    }

    private record PerfResult(long elapsedNs, double avgNsPerOp, double opsPerSec, long totalOps) {
    }

    @FunctionalInterface
    private interface IntAction {
        void execute(int value);
    }

    @FunctionalInterface
    private interface ThreadActionFactory {
        IntAction create();
    }
}
