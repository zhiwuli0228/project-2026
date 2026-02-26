package com.zhiwu.project2026.threadpool.schemec;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.integration.QueueCapacityController;
import com.zhiwu.project2026.threadpool.integration.ResizableQueueCapacityController;

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
        return reconcile(executor, new ResizableQueueCapacityController(queue), budgetInput, metrics, state, now);
    }

    public AppliedHybridResult reconcile(
            ThreadPoolExecutor executor,
            QueueCapacityController queueController,
            HybridBudgetInput budgetInput,
            HybridMetrics metrics,
            HybridControlState state,
            Instant now
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queueController, "queueController");
        Objects.requireNonNull(budgetInput, "budgetInput");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        HybridBudgetPlan budget = budgetCalculator.calculate(budgetInput);
        int currentCore = executor.getCorePoolSize();
        HybridSizingDecision decision = sizingController.decide(currentCore, budget, metrics, state, now);
        if (!queueController.supportsDynamicResize()) {
            decision = new HybridSizingDecision(
                    decision.targetCorePoolSize(),
                    queueController.currentCapacity(),
                    decision.action(),
                    decision.reason() + " (queue resize unsupported)"
            );
        }

        applyCoreAsFixedPool(executor, decision.targetCorePoolSize());

        int currentQueueSize = queueController.currentSize();
        int appliedQueueCapacity;
        boolean queueShrinkDeferred;
        if (queueController.supportsDynamicResize()) {
            appliedQueueCapacity = Math.max(decision.targetQueueCapacity(), currentQueueSize);
            queueShrinkDeferred = appliedQueueCapacity != decision.targetQueueCapacity();
            queueController.resizeCapacity(appliedQueueCapacity);
        } else {
            appliedQueueCapacity = queueController.currentCapacity();
            queueShrinkDeferred = false;
        }

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
