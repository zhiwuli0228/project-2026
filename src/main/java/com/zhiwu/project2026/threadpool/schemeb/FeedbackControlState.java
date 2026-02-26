package com.zhiwu.project2026.threadpool.schemeb;

import com.zhiwu.project2026.threadpool.gating.ScaleUpGateState;

import java.time.Instant;

public class FeedbackControlState {

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
