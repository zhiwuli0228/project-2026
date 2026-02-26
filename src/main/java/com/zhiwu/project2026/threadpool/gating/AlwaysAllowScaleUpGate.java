package com.zhiwu.project2026.threadpool.gating;

public class AlwaysAllowScaleUpGate implements ScaleUpGate {

    @Override
    public ScaleUpGateVerdict evaluate(ScaleUpGateInput input, ScaleUpGateState state) {
        return new ScaleUpGateVerdict(true, PressureLevel.CRITICAL, "always allow");
    }
}
