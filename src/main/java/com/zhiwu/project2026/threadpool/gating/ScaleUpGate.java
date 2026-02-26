package com.zhiwu.project2026.threadpool.gating;

public interface ScaleUpGate {

    ScaleUpGateVerdict evaluate(ScaleUpGateInput input, ScaleUpGateState state);
}
