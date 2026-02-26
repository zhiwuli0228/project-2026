package com.zhiwu.project2026.distribute.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusTest {

    @Test
    void shouldContainExpectedEnumConstants() {
        assertArrayEquals(
                new Status[]{Status.RUNNING, Status.SUCCESS, Status.FAIL},
                Status.values()
        );
        assertEquals(Status.SUCCESS, Status.valueOf("SUCCESS"));
    }
}
