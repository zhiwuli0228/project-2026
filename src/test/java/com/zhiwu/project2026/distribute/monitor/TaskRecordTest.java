package com.zhiwu.project2026.distribute.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskRecordTest {

    @Test
    void shouldInitializeAsRunning() {
        TaskRecord record = new TaskRecord(1000);

        assertEquals(1000, record.getStartMs());
        assertEquals(0, record.getEndMs());
        assertEquals(Status.RUNNING, record.getStatus());
        assertEquals(0, record.getDurationMs());
    }

    @Test
    void shouldMarkSuccessAndComputeDuration() {
        TaskRecord record = new TaskRecord(1000);

        record.markSuccess(1300);

        assertEquals(1300, record.getEndMs());
        assertEquals(Status.SUCCESS, record.getStatus());
        assertEquals(300, record.getDurationMs());
    }

    @Test
    void shouldMarkFailAndClampNegativeDurationToZero() {
        TaskRecord record = new TaskRecord(1000);

        record.markFail(900);

        assertEquals(900, record.getEndMs());
        assertEquals(Status.FAIL, record.getStatus());
        assertEquals(0, record.getDurationMs());
    }
}
