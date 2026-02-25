# 线程池容量动态调整设计文档

## 1. 背景与目标

当前服务存在以下现实约束：
- 服务会发生水平扩容/缩容（Pod 或实例数量变化）。
- 单实例 JVM `-Xmx` 在不同环境下不一致，甚至会动态变更（重启后生效）。
- 固定线程池参数（`corePoolSize`、队列容量）在不同实例规格下容易过大或过小。

目标：
- 让线程池核心线程数与队列容量可依据“实例可用内存 + 集群副本规模 + 实时负载”动态调整。
- 保证吞吐与延迟平衡，避免 OOM、过度排队与频繁抖动。
- 方案可灰度、可回滚、可观测。

非目标：
- 不追求完全自适应到“零人工参数”；仍保留边界与兜底阈值。

## 2. 设计原则

- 安全优先：任何时刻都不突破内存安全水位。
- 平滑调整：避免每秒级频繁调参导致抖动。
- 分层决策：先算“静态上限”（基于 `Xmx`/副本数），再做“动态微调”（基于实时指标）。
- 可解释性：参数来源可追溯，便于排障与审计。

## 3. 关键输入与指标

### 3.1 静态输入（重启后变化）
- `XmxBytes`：JVM 最大堆。
- `replicaCount`：当前服务副本数。
- `taskAvgHeapBytes`：单任务平均堆占用（压测/线上采样得到）。
- `taskP95HeapBytes`：单任务 P95 堆占用。

### 3.2 动态输入（运行时变化）
- `queueWaitP95Ms`：队列等待 P95。
- `execTimeP95Ms`：任务执行 P95。
- `activeCount/corePoolSize`：线程繁忙度。
- `rejectionRate`：拒绝率。
- `heapUsedRatio`：堆使用率。
- `gcPauseP95Ms`：GC 暂停 P95。

## 4. 可行方案

## 方案 A：规则驱动（Xmx + 副本数）离散分档

### 思路
按 `Xmx` 和 `replicaCount` 映射到预定义档位，定时（如 30s）校准线程池参数。

### 示例规则
- 基础线程数：`baseCore = clamp(2, cpu*2, 64)`
- 内存约束线程上限：`memBoundCore = floor((XmxBytes * 0.25) / taskP95HeapBytes)`
- 副本约束：`replicaFactor = sqrt(referenceReplica / replicaCount)`
- 最终核心线程：`core = clamp(minCore, floor(min(baseCore, memBoundCore) * replicaFactor), maxCore)`
- 队列容量：`queue = clamp(minQueue, core * queuePerThread, maxQueue)`

### 优点
- 实现快，变更风险小。
- 参数可解释，适合先上线。

### 缺点
- 仅规则，无法及时响应瞬时流量尖峰。
- 规则维护成本随业务复杂度上升。

适用场景：初期落地、对稳定性要求高、历史数据不足。

---

## 方案 B：反馈闭环（基于 SLO 的控制器）

### 思路
使用控制器（P/PI/PID 的简化实现）围绕目标 SLO（如 `queueWaitP95Ms <= 100ms`）动态调节 `corePoolSize` 与 `queueCapacity`。

### 控制逻辑（简化）
- 若 `queueWaitP95Ms` 连续 N 个周期超阈值，且 `heapUsedRatio < 0.75`，增加 `core`。
- 若 `heapUsedRatio > 0.82` 或 `gcPauseP95Ms` 超阈值，降低 `core` 并收缩队列。
- 每次调整步长受限（如 `±10%`），并设置冷却时间（如 2~5 分钟）。

### 优点
- 能自动适应流量波动，SLO 对齐更好。
- 比纯静态规则更节省资源。

### 缺点
- 调参复杂，需防止振荡。
- 对监控质量要求高。

适用场景：有稳定监控体系、可持续调优团队。

---

## 方案 C：两层混合（推荐）

### 思路
- 第一层“容量预算层”：基于 `Xmx` + `replicaCount` 算安全上限/下限。
- 第二层“运行控制层”：在预算区间内根据实时指标微调。

### 运行机制
1. 预算层给出：`coreMin/coreMax/queueMin/queueMax`。
2. 控制层每 30s 评估指标，只能在预算区间调整。
3. 当 `Xmx` 或副本数变化时，重算预算并触发平滑迁移。

### 优点
- 兼顾安全性与自适应能力。
- 比纯反馈更易控，比纯规则更智能。

### 缺点
- 架构较复杂，需要更多工程实现。

适用场景：中长期生产方案。

---

## 方案 D：离线建模 + 在线推断

### 思路
利用历史数据训练回归模型（输入：流量、任务特征、`Xmx`、副本数；输出：线程/队列推荐值），在线周期性推断。

### 优点
- 对复杂非线性场景可能最优。

### 缺点
- 建模、特征、漂移治理成本高。
- 可解释性与稳定性治理难度大。

适用场景：高成熟度平台团队，不建议作为第一阶段。

## 5. 方案对比

| 维度 | 方案 A 规则驱动 | 方案 B 反馈闭环 | 方案 C 两层混合 | 方案 D 建模推断 |
|---|---|---|---|---|
| 实现复杂度 | 低 | 中 | 中高 | 高 |
| 稳定性 | 高 | 中（依赖调参） | 高 | 中 |
| 自适应能力 | 低~中 | 高 | 高 | 高 |
| 可解释性 | 高 | 中 | 高 | 低~中 |
| 上线风险 | 低 | 中 | 中 | 高 |
| 推荐级别 | 短期可用 | 可选 | 强烈推荐 | 后期探索 |

## 6. 推荐方案（C）详细设计

### 6.1 参数模型

预算层：
- `coreUpperByMem = floor((XmxBytes * memBudgetRatio) / taskP95HeapBytes)`
- `coreUpperByCpu = cpuCore * cpuThreadFactor`
- `coreMax = clamp(minCore, min(coreUpperByMem, coreUpperByCpu), hardMaxCore)`
- `coreMin = max(hardMinCore, floor(coreMax * minCoreRatio))`

副本修正：
- `scaleFactor = (referenceReplica / replicaCount)^alpha`，`alpha` 建议 `0.3~0.5`。
- `coreMin/coreMax` 同步乘以 `scaleFactor` 后再 clamp。

队列预算：
- `queueMaxByMem = floor((XmxBytes * queueMemBudgetRatio) / taskAvgHeapBytes)`
- `queueMax = min(queueMaxByMem, coreMax * queuePerThreadUpper)`
- `queueMin = max(hardMinQueue, coreMin * queuePerThreadLower)`

控制层（每 30s）：
- 目标：`queueWaitP95Ms <= targetQueueWaitMs`
- 若超标且 `activeRatio > 0.85` 且 `heapUsedRatio < heapGuardLow`，`core += stepUp`
- 若 `heapUsedRatio > heapGuardHigh` 或 `gcPauseP95Ms` 超标，`core -= stepDown`
- 队列与 `core` 联动，保持 `queue/core` 在阈值区间。

### 6.2 防抖与保护
- 冷却窗口：单次调整后 120s 内不再调整。
- 步长限制：每次最多调整 `max(1, floor(core*0.1))`。
- 熔断：出现连续 M 次 OOM 预警或高 GC，强制切换到保守配置。
- 回退：支持动态开关，秒级回退到静态配置。

### 6.3 配置示例（application.yml）

```yaml
threadpool:
  adaptive:
    enabled: true
    mode: HYBRID
    evaluate-interval: 30s
    cooldown: 120s

    hard:
      min-core: 2
      max-core: 64
      min-queue: 100
      max-queue: 20000

    budget:
      mem-budget-ratio: 0.25
      queue-mem-budget-ratio: 0.12
      cpu-thread-factor: 2.0
      min-core-ratio: 0.35
      queue-per-thread-lower: 20
      queue-per-thread-upper: 400

    scaling:
      reference-replica: 4
      alpha: 0.4

    guard:
      heap-guard-low: 0.75
      heap-guard-high: 0.82
      target-queue-wait-ms: 100
      gc-pause-p95-ms: 200
```

### 6.4 实现建议（Spring）
- 封装 `AdaptiveThreadPoolManager`：负责采集指标、计算目标值、执行调整。
- 使用可动态变更队列容量的实现（如自定义 `ResizableCapacityLinkedBlockingQueue`）。
- 线程池调整通过 `ThreadPoolExecutor#setCorePoolSize/setMaximumPoolSize`。
- 指标接入 Micrometer + Prometheus，关键告警接入 Alertmanager。

### 6.5 伪代码

```java
void reconcile() {
    Metrics m = metricsCollector.snapshot();
    Budget b = budgetCalculator.recalculateIfNeeded(xmx(), replicaCount());

    int currentCore = executor.getCorePoolSize();
    int targetCore = currentCore;

    if (m.heapUsedRatio > cfg.heapGuardHigh || m.gcPauseP95Ms > cfg.gcPauseThreshold) {
        targetCore = currentCore - stepDown(currentCore);
    } else if (m.queueWaitP95Ms > cfg.targetQueueWaitMs
            && m.activeRatio > 0.85
            && m.heapUsedRatio < cfg.heapGuardLow) {
        targetCore = currentCore + stepUp(currentCore);
    }

    targetCore = clamp(b.coreMin, targetCore, b.coreMax);
    int targetQueue = clamp(b.queueMin, queueByCore(targetCore), b.queueMax);

    if (cooldownPassed() && changedEnough(targetCore, targetQueue)) {
        apply(targetCore, targetQueue);
    }
}
```

## 7. 落地路线图

1. Phase 1（1~2 周）：上线方案 A（规则驱动），打通指标与动态配置开关。
2. Phase 2（2~4 周）：引入方案 C 的控制层，但默认只读（不执行），先观测推荐值。
3. Phase 3（1~2 周）：灰度开启自动调整（10% -> 30% -> 100%）。
4. Phase 4（持续）：基于线上数据微调参数，评估是否需要方案 D。

## 8. 风险与应对

- 风险：控制振荡导致延迟抖动。
  - 应对：冷却窗口 + 步长限制 + 双阈值迟滞（hysteresis）。

- 风险：任务内存估计不准引起 OOM。
  - 应对：使用 P95/P99 估计；保留 20% 安全余量；高水位强制降配。

- 风险：副本数感知延迟。
  - 应对：使用 K8s API + 本地缓存 + 超时回退默认值。

- 风险：队列动态缩容行为不安全。
  - 应对：只允许扩容实时生效，缩容通过“新建池平滑切换”完成。

## 9. 验收标准

- 在同等流量下，`queueWaitP95Ms` 相比静态配置下降 >= 20%。
- 无新增 OOM，`heapUsedRatio > 0.9` 的持续时长下降 >= 30%。
- 拒绝率在目标区间内（如 < 0.1%）。
- 扩缩容后 5 分钟内恢复到目标 SLO。

## 10. 结论

- 短期可先落地方案 A，快速获得安全收益。
- 中长期建议采用方案 C（两层混合）作为生产标准方案：
  - 预算层确保不会越过内存与副本约束；
  - 控制层保证在波动流量下持续贴近延迟目标。
