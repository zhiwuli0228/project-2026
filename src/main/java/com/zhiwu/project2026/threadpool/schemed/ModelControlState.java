package com.zhiwu.project2026.threadpool.schemed;

import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;

import java.time.Instant;

public class ModelControlState {

    private final ScaleUpGateState scaleUpGateState = new ScaleUpGateState();
    private Instant lastAdjustmentAt;

    public ScaleUpGateState getScaleUpGateState() {
        return scaleUpGateState;
    }

    public int getConsecutiveQueueWaitBreaches() {
        return scaleUpGateState.getConsecutiveQueueWaitBreaches();
    }

    public void resetQueueWaitBreaches() {
        scaleUpGateState.resetQueueWaitBreaches();
    }

    public Instant getLastAdjustmentAt() {
        return lastAdjustmentAt;
    }

    public void setLastAdjustmentAt(Instant lastAdjustmentAt) {
        this.lastAdjustmentAt = lastAdjustmentAt;
    }
}
