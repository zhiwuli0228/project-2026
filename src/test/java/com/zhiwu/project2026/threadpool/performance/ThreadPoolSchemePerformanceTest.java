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
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetInput;
import com.zhiwu.project2026.threadpool.schemec.HybridControlState;
import com.zhiwu.project2026.threadpool.schemec.HybridMetrics;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingConfig;
import com.zhiwu.project2026.threadpool.schemec.HybridThreadPoolManager;
import com.zhiwu.project2026.threadpool.schemec.HybridBudgetCalculator;
import com.zhiwu.project2026.threadpool.schemec.HybridSizingController;
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
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolSchemePerformanceTest {

    private static final int WARMUP = 5_000;
    private static final int ITERATIONS = 50_000;
    private static final double MAX_AVG_NS_PER_OP = 2_000_000.0;

    @Test
    void benchmarkSchemeAReconcile() {
        RuleBasedSizingConfig config = new RuleBasedSizingConfig(
                2, 64, 2.0, 0.25, 100, 100, 20_000, 4
        );
        RuleBasedThreadPoolManager manager = new RuleBasedThreadPoolManager(new RuleBasedSizingCalculator(config));
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
        SizingInput input = new SizingInput(
                8L * 1024 * 1024 * 1024,
                4,
                32L * 1024 * 1024,
                8
        );

        PerfResult result = runBenchmark(i -> manager.reconcile(executor, queue, input));
        print("SchemeA", result);
        assertTrue(result.avgNsPerOp < MAX_AVG_NS_PER_OP);
    }

    @Test
    void benchmarkSchemeBReconcile() {
        FeedbackControllerConfig config = new FeedbackControllerConfig(
                2, 64, 100, 20_000, 100,
                100, 0.85, 3, 0.75, 0.82, 200, 0.1, Duration.ofMinutes(2)
        );
        FeedbackBasedThreadPoolManager manager =
                new FeedbackBasedThreadPoolManager(new FeedbackSizingController(config));
        FeedbackControlState state = new FeedbackControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackMetrics metrics = new FeedbackMetrics(130, 220, 0.92, 0.01, 0.60, 80);

        PerfResult result = runBenchmark(i -> manager.reconcile(executor, queue, metrics, state, base.plusSeconds(i)));
        print("SchemeB", result);
        assertTrue(result.avgNsPerOp < MAX_AVG_NS_PER_OP);
    }

    @Test
    void benchmarkSchemeCReconcile() {
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
        HybridControlState state = new HybridControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        HybridBudgetInput input = new HybridBudgetInput(
                8L * 1024 * 1024 * 1024,
                4,
                4L * 1024 * 1024,
                32L * 1024 * 1024,
                8
        );
        HybridMetrics metrics = new HybridMetrics(130, 220, 0.92, 0.01, 0.60, 80);

        PerfResult result = runBenchmark(i -> manager.reconcile(executor, queue, input, metrics, state, base.plusSeconds(i)));
        print("SchemeC", result);
        assertTrue(result.avgNsPerOp < MAX_AVG_NS_PER_OP);
    }

    @Test
    void benchmarkSchemeDReconcile() {
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
        ModelControlState state = new ModelControlState();
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(1000);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, queue);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ModelFeatures features = new ModelFeatures(
                200.0, 130, 220, 0.92, 0.01, 0.60, 80,
                8L * 1024 * 1024 * 1024, 4, 8
        );

        PerfResult result = runBenchmark(i -> manager.reconcile(executor, queue, features, state, base.plusSeconds(i)));
        print("SchemeD", result);
        assertTrue(result.avgNsPerOp < MAX_AVG_NS_PER_OP);
    }

    private PerfResult runBenchmark(IntAction action) {
        for (int i = 0; i < WARMUP; i++) {
            action.execute(i);
        }
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            action.execute(i);
        }
        long elapsedNs = System.nanoTime() - start;
        double avgNs = elapsedNs / (double) ITERATIONS;
        double opsPerSec = 1_000_000_000.0 / avgNs;
        return new PerfResult(elapsedNs, avgNs, opsPerSec);
    }

    private void print(String scheme, PerfResult result) {
        System.out.println(String.format(
                Locale.ROOT,
                "PERF %s elapsedMs=%.3f avgNs=%.3f opsPerSec=%.2f",
                scheme,
                result.elapsedNs / 1_000_000.0,
                result.avgNsPerOp,
                result.opsPerSec
        ));
    }

    private record PerfResult(long elapsedNs, double avgNsPerOp, double opsPerSec) {
    }

    @FunctionalInterface
    private interface IntAction {
        void execute(int value);
    }
}
