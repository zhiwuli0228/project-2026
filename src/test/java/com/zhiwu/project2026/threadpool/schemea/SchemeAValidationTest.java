package com.zhiwu.project2026.threadpool.schemea;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemeAValidationTest {

    @Test
    void shouldValidateSizingPlan() {
        assertThrows(IllegalArgumentException.class, () -> new SizingPlan(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SizingPlan(1, 0));
    }

    @Test
    void shouldValidateAppliedSizingResultConsistency() {
        SizingPlan plan = new SizingPlan(2, 200);

        assertThrows(NullPointerException.class, () -> new AppliedSizingResult(null, 200, false));
        assertThrows(IllegalArgumentException.class, () -> new AppliedSizingResult(plan, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new AppliedSizingResult(plan, 200, true));
        assertThrows(IllegalArgumentException.class, () -> new AppliedSizingResult(plan, 250, false));
    }

    @Test
    void shouldRejectNullTimeUnitInTimedOperations() {
        ResizableCapacityBlockingQueue<Runnable> queue = new ResizableCapacityBlockingQueue<>(10);
        Runnable task = () -> { };

        assertThrows(NullPointerException.class, () -> queue.offer(task, 1, null));
        assertThrows(NullPointerException.class, () -> queue.poll(1, null));
    }
}
