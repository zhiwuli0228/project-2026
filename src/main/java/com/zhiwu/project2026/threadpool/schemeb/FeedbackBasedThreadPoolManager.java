package com.zhiwu.project2026.threadpool.schemeb;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

public class FeedbackBasedThreadPoolManager {

    private final FeedbackSizingController controller;

    public FeedbackBasedThreadPoolManager(FeedbackSizingController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public AppliedFeedbackResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            FeedbackMetrics metrics,
            FeedbackControlState state,
            Instant now
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queue, "queue");

        int currentCore = executor.getCorePoolSize();
        FeedbackSizingDecision decision = controller.decide(currentCore, metrics, state, now);
        applyCoreAsFixedPool(executor, decision.targetCorePoolSize());

        int currentQueueSize = queue.size();
        int appliedQueueCapacity = Math.max(decision.targetQueueCapacity(), currentQueueSize);
        boolean queueShrinkDeferred = appliedQueueCapacity != decision.targetQueueCapacity();
        queue.setCapacity(appliedQueueCapacity);

        return new AppliedFeedbackResult(decision, appliedQueueCapacity, queueShrinkDeferred);
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
