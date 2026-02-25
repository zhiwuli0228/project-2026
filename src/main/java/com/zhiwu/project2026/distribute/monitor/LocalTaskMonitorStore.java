package com.zhiwu.project2026.distribute.monitor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/2/25 23:08
 */
@Component
public class LocalTaskMonitorStore {
    // taskId -> running
    private final Map<String, TaskRecord> running = new ConcurrentHashMap<>();

    // taskId -> success history
    private final Map<String, ConcurrentLinkedDeque<TaskRecord>> successHist = new ConcurrentHashMap<>();
    // taskId -> fail history
    private final Map<String, ConcurrentLinkedDeque<TaskRecord>> failHist = new ConcurrentHashMap<>();


    // ================= lifecycle =================

    public void recordStart(String taskId, long startMs) {
        running.put(taskId, new TaskRecord(startMs));
    }

    public void recordSuccess(String taskId, long periodSec, long endMs) {
        TaskRecord r = running.remove(taskId);
        if (r == null) {
            return;
        }
        r.markSuccess(endMs);

        ConcurrentLinkedDeque<TaskRecord> q =
                successHist.computeIfAbsent(taskId, k -> new ConcurrentLinkedDeque<>());
        q.addLast(r);

        trimByCount(taskId, periodSec);
    }

    public void recordFail(String taskId, long periodSec, long endMs) {
        TaskRecord r = running.remove(taskId);
        if (r == null) {
            return;
        }
        r.markFail(endMs);

        ConcurrentLinkedDeque<TaskRecord> q =
                failHist.computeIfAbsent(taskId, k -> new ConcurrentLinkedDeque<>());
        q.addLast(r);

        trimByCount(taskId, periodSec);
    }

    public Optional<TaskRecord> getRunning(String taskId) {
        return Optional.ofNullable(running.get(taskId));
    }

    // ================= query =================

    /**
     * 返回最近 N 条完成记录（fail 优先靠前或按 endMs 排序你可自行选择）
     */
    public List<TaskRecord> listRecentCompleted(String taskId) {


        var s = successHist.getOrDefault(taskId, new ConcurrentLinkedDeque<>());
        var f = failHist.getOrDefault(taskId, new ConcurrentLinkedDeque<>());

        // 简单策略：合并后按 endMs 倒序（排障调用，O(n log n) 可接受）
        List<TaskRecord> all = new ArrayList<>(s.size() + f.size());
        all.addAll(s);
        all.addAll(f);
        return all;
    }

    // ================= retention =================

    private void trimByCount(String taskId, long periodSec) {
        int keepS = RetentionPolicy.keepSuccessCount(periodSec);
        int keepF = RetentionPolicy.keepFailCount(periodSec);

        ConcurrentLinkedDeque<TaskRecord> s = successHist.get(taskId);
        if (s != null) {
            trimQueue(s, keepS);
        }

        ConcurrentLinkedDeque<TaskRecord> f = failHist.get(taskId);
        if (f != null) {
            trimQueue(f, keepF);
        }
    }

    private static void trimQueue(ConcurrentLinkedDeque<TaskRecord> q, int keep) {
        if (keep < 0) {
            keep = 0;
        }
        while (q.size() > keep) {
            q.pollFirst(); // remove oldest
        }
    }
}
