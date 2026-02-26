package com.zhiwu.project2026.threadpool.gating;

import java.util.Objects;

public record ScaleUpGateVerdict(
        boolean allowScaleUp,
        PressureLevel pressureLevel,
        String reason
) {
    public ScaleUpGateVerdict {
        pressureLevel = Objects.requireNonNull(pressureLevel, "pressureLevel");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
