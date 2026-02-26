package com.zhiwu.project2026.threadpool.schemed;

import com.zhiwu.project2026.threadpool.schemea.ResizableCapacityBlockingQueue;
import com.zhiwu.project2026.threadpool.integration.QueueCapacityController;
import com.zhiwu.project2026.threadpool.integration.ResizableQueueCapacityController;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

public class ModelBasedThreadPoolManager {

    private final ModelBasedSizingController controller;

    public ModelBasedThreadPoolManager(ModelBasedSizingController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public AppliedModelResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            ModelFeatures features,
            ModelControlState state,
            Instant now
    ) {
        return reconcile(executor, new ResizableQueueCapacityController(queue), features, state, now);
    }

    public AppliedModelResult reconcile(
            ThreadPoolExecutor executor,
            QueueCapacityController queueController,
            ModelFeatures features,
            ModelControlState state,
            Instant now
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queueController, "queueController");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");

        int currentCore = executor.getCorePoolSize();
        ModelSizingDecision decision = controller.decide(currentCore, features, state, now);
        if (!queueController.supportsDynamicResize()) {
            decision = new ModelSizingDecision(
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

        return new AppliedModelResult(decision, appliedQueueCapacity, queueShrinkDeferred);
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
