package com.zhiwu.project2026.distribute.monitor;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/2/25 23:09
 */
public class TaskRecord {
    private final long startMs;
    private long endMs;
    private Status status;
    private int durationMs;

    public TaskRecord(long startMs) {
        this.startMs = startMs;
        this.status = Status.RUNNING;
    }

    public void markSuccess(long endMs) {
        this.endMs = endMs;
        this.durationMs = (int) Math.max(0, endMs - startMs);
        this.status = Status.SUCCESS;
    }

    public void markFail(long endMs) {
        this.endMs = endMs;
        this.durationMs = (int) Math.max(0, endMs - startMs);
        this.status = Status.FAIL;
    }

    public long getStartMs() { return startMs; }
    public long getEndMs() { return endMs; }
    public Status getStatus() { return status; }
    public int getDurationMs() { return durationMs; }
}
