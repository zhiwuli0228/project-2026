package com.zhiwu.project2026.threadpool.schemeb;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.integration.QueueCapacityController;
import com.zhiwu.project2026.threadpool.integration.ResizableQueueCapacityController;

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
        return reconcile(executor, new ResizableQueueCapacityController(queue), metrics, state, now);
    }

    public AppliedFeedbackResult reconcile(
            ThreadPoolExecutor executor,
            QueueCapacityController queueController,
            FeedbackMetrics metrics,
            FeedbackControlState state,
            Instant now
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queueController, "queueController");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        int currentCore = executor.getCorePoolSize();
        FeedbackSizingDecision decision = controller.decide(currentCore, metrics, state, now);
        if (!queueController.supportsDynamicResize()) {
            decision = new FeedbackSizingDecision(
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
