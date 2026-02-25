package com.zhiwu.project2026.threadpool.schemec;

import java.time.Instant;

public class HybridControlState {

    private int consecutiveQueueWaitBreaches;
    private Instant lastAdjustmentAt;

    public int getConsecutiveQueueWaitBreaches() {
        return consecutiveQueueWaitBreaches;
    }

    public void incrementQueueWaitBreaches() {
        consecutiveQueueWaitBreaches++;
    }

    public void resetQueueWaitBreaches() {
        consecutiveQueueWaitBreaches = 0;
    }

    public Instant getLastAdjustmentAt() {
        return lastAdjustmentAt;
    }

    public void setLastAdjustmentAt(Instant lastAdjustmentAt) {
        this.lastAdjustmentAt = lastAdjustmentAt;
    }
}
