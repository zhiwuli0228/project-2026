package com.zhiwu.project2026.distribute.monitor;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/2/25 23:30
 */
public class RetentionPolicy {
    private static final int retentionDays = 3;      // e.g. 2
    private static final int maxKeepSuccess = 1000;     // e.g. 2000
    private static final int failMultiplier = 2;     // e.g. 3
    private static final int maxKeepFail = 2000;        // e.g. 5000



    public static int keepSuccessCount(long periodSec) {
        if (periodSec <= 0) {
            return Math.min(1, maxKeepSuccess);
        }
        long need = divCeil((long) retentionDays * 86400L, periodSec);
        return (int) Math.min(need, (long) maxKeepSuccess);
    }

    public static int keepFailCount(long periodSec) {
        long base = keepSuccessCount(periodSec);
        long need = base * (long) failMultiplier;
        return (int) Math.min(need, maxKeepFail);
    }

    private static long divCeil(long a, long b) {
        return (a + b - 1) / b;
    }
}
