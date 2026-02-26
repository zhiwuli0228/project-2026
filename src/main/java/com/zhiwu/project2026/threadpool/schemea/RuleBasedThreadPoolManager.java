package com.zhiwu.project2026.threadpool.schemea;

import com.zhiwu.project2026.threadpool.gating.AlwaysAllowScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGate;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateInput;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;
import com.zhiwu.project2026.threadpool.gating.ScaleUpGateVerdict;
import com.zhiwu.project2026.threadpool.integration.QueueCapacityController;
import com.zhiwu.project2026.threadpool.integration.ResizableQueueCapacityController;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

public class RuleBasedThreadPoolManager {

    private final RuleBasedSizingCalculator calculator;
    private final ScaleUpGate scaleUpGate;

    public RuleBasedThreadPoolManager(RuleBasedSizingCalculator calculator) {
        this(calculator, new AlwaysAllowScaleUpGate());
    }

    public RuleBasedThreadPoolManager(RuleBasedSizingCalculator calculator, ScaleUpGate scaleUpGate) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.scaleUpGate = Objects.requireNonNull(scaleUpGate, "scaleUpGate");
    }

    public AppliedSizingResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            SizingInput input
    ) {
        return reconcile(executor, new ResizableQueueCapacityController(queue), input, null, null);
    }

    public AppliedSizingResult reconcile(
            ThreadPoolExecutor executor,
            ResizableCapacityBlockingQueue<Runnable> queue,
            SizingInput input,
            ScaleUpGateInput gateInput,
            ScaleUpGateState gateState
    ) {
        return reconcile(executor, new ResizableQueueCapacityController(queue), input, gateInput, gateState);
    }

    public AppliedSizingResult reconcile(
            ThreadPoolExecutor executor,
            QueueCapacityController queueController,
            SizingInput input,
            ScaleUpGateInput gateInput,
            ScaleUpGateState gateState
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(queueController, "queueController");
        Objects.requireNonNull(input, "input");

        SizingPlan plan = calculator.calculate(input);
        if (plan.corePoolSize() > executor.getCorePoolSize() && gateInput != null && gateState != null) {
            ScaleUpGateVerdict gateVerdict = scaleUpGate.evaluate(gateInput, gateState);
            if (!gateVerdict.allowScaleUp()) {
                plan = new SizingPlan(executor.getCorePoolSize(), queueController.currentCapacity());
            }
        }
        if (!queueController.supportsDynamicResize()) {
            plan = new SizingPlan(plan.corePoolSize(), queueController.currentCapacity());
        }
        applyCoreAsFixedPool(executor, plan.corePoolSize());

        int currentQueueSize = queueController.currentSize();
        int appliedQueueCapacity;
        boolean queueShrinkDeferred;
        if (queueController.supportsDynamicResize()) {
            appliedQueueCapacity = Math.max(plan.queueCapacity(), currentQueueSize);
            queueShrinkDeferred = appliedQueueCapacity != plan.queueCapacity();
            queueController.resizeCapacity(appliedQueueCapacity);
        } else {
            appliedQueueCapacity = queueController.currentCapacity();
            queueShrinkDeferred = false;
        }

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
