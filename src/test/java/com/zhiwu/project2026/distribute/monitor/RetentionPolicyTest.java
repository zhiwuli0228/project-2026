package com.zhiwu.project2026.distribute.monitor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetentionPolicyTest {

    @ParameterizedTest
    @MethodSource("successCases")
    void shouldComputeSuccessRetentionByDecisionTable(long periodSec, int expectedKeep) {
        assertEquals(expectedKeep, RetentionPolicy.keepSuccessCount(periodSec));
    }

    @ParameterizedTest
    @MethodSource("failCases")
    void shouldComputeFailRetentionByDecisionTable(long periodSec, int expectedKeep) {
        assertEquals(expectedKeep, RetentionPolicy.keepFailCount(periodSec));
    }

    @Test
    void shouldApplyFailMultiplierWhenNotCapped() {
        int successKeep = RetentionPolicy.keepSuccessCount(300);
        assertEquals(successKeep * 2, RetentionPolicy.keepFailCount(300));
    }

    private static Stream<Arguments> successCases() {
        return Stream.of(
                Arguments.of(-1L, 1),
                Arguments.of(0L, 1),
                Arguments.of(259_201L, 1),
                Arguments.of(259_200L, 1),
                Arguments.of(300L, 864),
                Arguments.of(60L, 1000)
        );
    }

    private static Stream<Arguments> failCases() {
        return Stream.of(
                Arguments.of(0L, 2),
                Arguments.of(259_200L, 2),
                Arguments.of(300L, 1728),
                Arguments.of(60L, 2000)
        );
    }
}
