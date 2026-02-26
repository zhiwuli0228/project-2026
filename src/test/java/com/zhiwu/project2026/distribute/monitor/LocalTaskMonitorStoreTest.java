package com.zhiwu.project2026.distribute.monitor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTaskMonitorStoreTest {

    @Test
    void shouldTrackRunningTaskAfterStart() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();

        store.recordStart("task-A", 1_000);

        TaskRecord running = store.getRunning("task-A").orElseThrow();
        assertEquals(1_000, running.getStartMs());
        assertEquals(Status.RUNNING, running.getStatus());
        assertFalse(store.getRunning("task-B").isPresent());
    }

    @Test
    void shouldMoveRunningTaskToSuccessHistory() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();
        store.recordStart("task-A", 1_000);

        store.recordSuccess("task-A", 300, 1_400);

        assertFalse(store.getRunning("task-A").isPresent());
        List<TaskRecord> completed = store.listRecentCompleted("task-A");
        TaskRecord first = completed.get(0);
        assertAll(
                () -> assertEquals(1, completed.size()),
                () -> assertEquals(Status.SUCCESS, first.getStatus()),
                () -> assertEquals(1_000, first.getStartMs()),
                () -> assertEquals(1_400, first.getEndMs()),
                () -> assertEquals(400, first.getDurationMs())
        );
    }

    @Test
    void shouldMoveRunningTaskToFailHistory() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();
        store.recordStart("task-A", 2_000);

        store.recordFail("task-A", 300, 2_200);

        assertFalse(store.getRunning("task-A").isPresent());
        List<TaskRecord> completed = store.listRecentCompleted("task-A");
        TaskRecord first = completed.get(0);
        assertAll(
                () -> assertEquals(1, completed.size()),
                () -> assertEquals(Status.FAIL, first.getStatus()),
                () -> assertEquals(2_000, first.getStartMs()),
                () -> assertEquals(2_200, first.getEndMs()),
                () -> assertEquals(200, first.getDurationMs())
        );
    }

    @Test
    void shouldIgnoreFinishEventWhenNoRunningRecordExists() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();

        store.recordSuccess("task-A", 300, 1_000);
        store.recordFail("task-A", 300, 1_000);

        assertTrue(store.listRecentCompleted("task-A").isEmpty());
    }

    @Test
    void shouldTrimHistoriesByRetentionPolicy() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();
        long periodSec = 259_200L;

        store.recordStart("task-A", 100);
        store.recordSuccess("task-A", periodSec, 200);
        store.recordStart("task-A", 300);
        store.recordSuccess("task-A", periodSec, 400);
        store.recordStart("task-A", 500);
        store.recordSuccess("task-A", periodSec, 600);

        store.recordStart("task-A", 700);
        store.recordFail("task-A", periodSec, 800);
        store.recordStart("task-A", 900);
        store.recordFail("task-A", periodSec, 1000);
        store.recordStart("task-A", 1100);
        store.recordFail("task-A", periodSec, 1200);

        List<TaskRecord> completed = store.listRecentCompleted("task-A");
        List<TaskRecord> success = completed.stream()
                .filter(r -> r.getStatus() == Status.SUCCESS)
                .collect(Collectors.toList());
        List<TaskRecord> fail = completed.stream()
                .filter(r -> r.getStatus() == Status.FAIL)
                .collect(Collectors.toList());

        assertEquals(1, success.size());
        assertEquals(600, success.get(0).getEndMs());
        assertEquals(2, fail.size());
        assertEquals(List.of(1000L, 1200L),
                fail.stream().map(TaskRecord::getEndMs).collect(Collectors.toList()));
    }

    @Test
    void shouldKeepTaskHistoriesIsolatedByTaskId() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();

        store.recordStart("task-A", 100);
        store.recordSuccess("task-A", 300, 200);
        store.recordStart("task-B", 300);
        store.recordFail("task-B", 300, 500);

        assertEquals(1, store.listRecentCompleted("task-A").size());
        assertEquals(Status.SUCCESS, store.listRecentCompleted("task-A").get(0).getStatus());
        assertEquals(1, store.listRecentCompleted("task-B").size());
        assertEquals(Status.FAIL, store.listRecentCompleted("task-B").get(0).getStatus());
    }

    @Test
    void shouldOverwriteRunningRecordWhenTaskStartsAgainBeforeFinish() {
        LocalTaskMonitorStore store = new LocalTaskMonitorStore();

        store.recordStart("task-A", 100);
        store.recordStart("task-A", 700);
        store.recordSuccess("task-A", 300, 1000);

        List<TaskRecord> completed = store.listRecentCompleted("task-A");
        TaskRecord first = completed.get(0);
        assertAll(
                () -> assertEquals(1, completed.size()),
                () -> assertEquals(700, first.getStartMs()),
                () -> assertEquals(1000, first.getEndMs()),
                () -> assertEquals(300, first.getDurationMs()),
                () -> assertEquals(Status.SUCCESS, first.getStatus())
        );
    }
}
