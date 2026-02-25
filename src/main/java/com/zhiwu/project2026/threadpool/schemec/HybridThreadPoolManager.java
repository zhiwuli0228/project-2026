package com.zhiwu.project2026.threadpool.schemec;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

public class HybridThreadPoolManager {

    private final HybridBudgetCalculator budgetCalculator;
    private final HybridSizingController sizingController;

    public HybridThreadPoolManager(HybridBudgetCalculator budgetCalculator, HybridSizingController sizingController) {
        this.budgetCalculator = Objects.requireNonNull(budgetCalculator, "budgetCalculator");
        this.sizingController = Objects.requireNonNull(sizingController, "sizingController");
    }

    public AppliedHybridResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            HybridBudgetInput budgetInput,
            HybridMetrics metrics,
            HybridControlState state,
            Instant now
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queue, "queue");

        HybridBudgetPlan budget = budgetCalculator.calculate(budgetInput);
        int currentCore = executor.getCorePoolSize();
        HybridSizingDecision decision = sizingController.decide(currentCore, budget, metrics, state, now);

        applyCoreAsFixedPool(executor, decision.targetCorePoolSize());

        int currentQueueSize = queue.size();
        int appliedQueueCapacity = Math.max(decision.targetQueueCapacity(), currentQueueSize);
        boolean queueShrinkDeferred = appliedQueueCapacity != decision.targetQueueCapacity();
        queue.setCapacity(appliedQueueCapacity);

        return new AppliedHybridResult(budget, decision, appliedQueueCapacity, queueShrinkDeferred);
    }

    private void applyCoreAsFixedPool(ThreadPoolExecutor executor, int targetCore) {
        if (targetCore > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(targetCore);
            executor.setCorePoolSize(targetCore);
            return;
        }
        executor.setCorePoolSize(targetCore);
        executor.setMaximumPoolSize(targetCore);
    }
}
