package com.zhiwu.project2026.threadpool.schemec;

import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;

import java.time.Instant;

public class HybridControlState {

    private final ScaleUpGateState scaleUpGateState = new ScaleUpGateState();
    private Instant lastAdjustmentAt;

    public int getConsecutiveQueueWaitBreaches() {
        return scaleUpGateState.getConsecutiveQueueWaitBreaches();
    }

    public void incrementQueueWaitBreaches() {
        scaleUpGateState.incrementQueueWaitBreaches();
    }

    public void resetQueueWaitBreaches() {
        scaleUpGateState.resetQueueWaitBreaches();
    }

    public ScaleUpGateState getScaleUpGateState() {
        return scaleUpGateState;
    }

    public Instant getLastAdjustmentAt() {
        return lastAdjustmentAt;
    }

    public void setLastAdjustmentAt(Instant lastAdjustmentAt) {
        this.lastAdjustmentAt = lastAdjustmentAt;
    }
}
