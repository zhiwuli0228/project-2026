package com.zhiwu.project2026.threadpool.gating;

public class ScaleUpGateState {

    private int consecutiveQueueWaitBreaches;

    public int getConsecutiveQueueWaitBreaches() {
        return consecutiveQueueWaitBreaches;
    }

    public void incrementQueueWaitBreaches() {
        consecutiveQueueWaitBreaches++;
    }

    public void resetQueueWaitBreaches() {
        consecutiveQueueWaitBreaches = 0;
    }
}
