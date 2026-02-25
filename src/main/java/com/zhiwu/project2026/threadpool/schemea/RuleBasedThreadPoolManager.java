package com.zhiwu.project2026.threadpool.schemea;

import java.util.concurrent.ThreadPoolExecutor;

public class RuleBasedThreadPoolManager {

    private final RuleBasedSizingCalculator calculator;

    public RuleBasedThreadPoolManager(RuleBasedSizingCalculator calculator) {
        this.calculator = calculator;
    }

    public AppliedSizingResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            SizingInput input
    ) {
        SizingPlan plan = calculator.calculate(input);
        applyCoreAsFixedPool(executor, plan.corePoolSize());

        int currentQueueSize = queue.size();
        int appliedQueueCapacity = Math.max(plan.queueCapacity(), currentQueueSize);
        boolean queueShrinkDeferred = appliedQueueCapacity != plan.queueCapacity();
        queue.setCapacity(appliedQueueCapacity);

        return new AppliedSizingResult(plan, appliedQueueCapacity, queueShrinkDeferred);
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

