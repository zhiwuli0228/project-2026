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
- 默认“尽量不动”，仅在真实压力持续出现时触发扩容。

非目标：
- 不追求完全自适应到“零人工参数”；仍保留边界与兜底阈值。

## 2. 设计原则

- 安全优先：任何时刻都不突破内存安全水位。
- 保守优先：无明显压力时不变更配置。
- 平滑调整：避免每秒级频繁调参导致抖动。
- 分层决策：先算“静态上限”（基于 `Xmx`/副本数），再做“动态微调”（基于实时指标）。
- 可解释性：参数来源可追溯，便于排障与审计。

### 2.1 统一压力管控框架（适用于 A/B/C/D）

为避免扩容过敏导致系统不稳定，所有方案统一引入以下机制：

1. 扩容闸门（Scale-Up Gate）
- 必须同时满足：
  - `queueWaitP95Ms` 连续 `N_up` 个周期超阈值；
  - `activeRatio >= busyThreshold`；
  - `heapUsedRatio < heapGuardLow`；
  - `gcPauseP95Ms < gcGuardSoft`。
- 任一条件不满足：不扩容，仅观察。

2. 压力分级（三级）
- `NORMAL`：保持不变。
- `ELEVATED`：只允许推荐值计算，不执行扩容（预热阶段）。
- `CRITICAL`：满足连续窗口后才执行小步扩容。

3. 最小变更阈值（Deadband）
- 若 `|targetCore-currentCore| < deltaCoreMin` 且 `|targetQueue-currentQueue| < deltaQueueMin`，则忽略本次变更。

4. 冷却窗口（Cooldown）
- 单次扩/缩容后 `cooldown` 时间内不再变更。

5. 连续观测窗口
- 扩容判定采用“连续超阈值计数”，缩容判定采用“连续低压计数”，避免单点毛刺触发。

6. 降压优先与保护
- 当 `heapUsedRatio > heapGuardHigh` 或 `gcPauseP95Ms` 超阈值时，优先限速或降配，不允许继续扩容。

7. 扩容限速
- 每次最多调整 `max(1, floor(core*stepRatio))`，禁止一次跳变到模型/规则推荐终值。

8. 队列缩容保护
- 仅当 `targetQueue >= currentQueueSize` 才执行缩容；否则延迟缩容，避免队列元素丢失风险。

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

### 压力管控增强
- 默认仅在静态输入变化（`Xmx/replica`）时重算并尝试变更。
- 对规则结果增加 deadband：核心线程变化 `< 2` 不执行。
- 加入低频校准（如 60s~120s）而非高频（30s）校准，减少无效变更。
- 当监控显示 `NORMAL` 压力级别时，即使规则结果变化也可延迟应用到下一个维护窗口。

### 优点
- 实现快，变更风险小。
- 参数可解释，适合先上线。

### 缺点
- 对瞬时流量尖峰响应弱。

适用场景：初期落地、对稳定性要求高、历史数据不足。

---

## 方案 B：反馈闭环（基于 SLO 的控制器）

### 思路
使用控制器围绕目标 SLO（如 `queueWaitP95Ms <= 100ms`）动态调节 `corePoolSize` 与 `queueCapacity`。

### 控制逻辑（增强）
- 扩容触发（必须全部满足）：
  - `queueWaitP95Ms` 连续 `N_up` 周期超阈值；
  - `activeRatio >= busyThreadRatioThreshold`；
  - `heapUsedRatio < heapGuardLow`；
  - `rejectionRate` 持续上升或超过软阈值（可选增强）。
- 缩容触发（必须全部满足）：
  - `queueWaitP95Ms` 连续 `N_down` 周期低于回落阈值（低于目标阈值的 60%~70%）；
  - `activeRatio` 连续偏低；
  - 无高压 GC/Heap 信号。
- 高压保护：
  - 若 `heapUsedRatio > heapGuardHigh` 或 `gcPauseP95Ms` 超阈值，禁止扩容并允许小步降配。
- 统一防抖：步长限制 + 冷却窗口 + deadband + 连续窗口。

### 优点
- 可自动适应流量波动，SLO 对齐更好。

### 缺点
- 调参复杂，对监控质量要求高。

适用场景：有稳定监控体系、可持续调优团队。

---

## 方案 C：两层混合（推荐）

### 思路
- 第一层“容量预算层”：基于 `Xmx` + `replicaCount` 算安全上限/下限。
- 第二层“运行控制层”：在预算区间内根据实时指标微调。

### 运行机制（增强）
1. 预算层给出：`coreMin/coreMax/queueMin/queueMax`。
2. 控制层每 30s 评估指标，只在“压力闸门打开”时尝试扩容。
3. 当 `Xmx` 或副本数变化时，重算预算并触发平滑迁移。
4. 若处于 `NORMAL` 压力级别：控制层仅记录推荐值，不立即执行变更。

### 压力管控增强
- 扩容必须满足：
  - `targetCore > currentCore + deltaCoreMin`；
  - 且连续 `N_up` 窗口均超阈值。
- 缩容必须满足：
  - 连续 `N_down` 窗口低压；
  - 且目标值不触发队列风险（`targetQueue >= currentQueueSize`，否则延迟）。
- 增加“压测模式参数集”与“生产保守参数集”双配置，生产默认更保守。

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

### 压力管控增强（必须）
- 模型输出不直接执行，必须经过控制闸门：
  - 先做边界裁剪（`min/max`）；
  - 再做步长裁剪（单步上限）；
  - 再过连续窗口与冷却校验。
- 引入模型置信度阈值：低置信度时仅记录推荐，不执行变更。
- 引入“模型漂移保护”：
  - 漂移超阈值时降级为方案 C 或 A。
- 引入“输出变化率限制”：
  - 防止模型版本切换导致建议值突变。

### 优点
- 对复杂非线性场景可能最优。

### 缺点
- 建模、特征、漂移治理成本高。
- 可解释性与稳定性治理难度大。

适用场景：高成熟度平台团队，不建议作为第一阶段主路径。

## 5. 方案对比（增加“扩容敏感性可控性”）

| 维度 | 方案 A 规则驱动 | 方案 B 反馈闭环 | 方案 C 两层混合 | 方案 D 建模推断 |
|---|---|---|---|---|
| 实现复杂度 | 低 | 中 | 中高 | 高 |
| 稳定性 | 高 | 中（依赖调参） | 高 | 中 |
| 自适应能力 | 低~中 | 高 | 高 | 高 |
| 可解释性 | 高 | 中 | 高 | 低~中 |
| 扩容敏感性可控性 | 高 | 中 | 高 | 中 |
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
- 扩容前置：满足统一扩容闸门（连续窗口 + busy + heap/gc 安全）。
- 扩容执行：`core += stepUp`（受步长和 deadband 限制）。
- 缩容执行：满足连续低压窗口后 `core -= stepDown`。
- 队列与 `core` 联动，保持 `queue/core` 在阈值区间。

### 6.2 防抖与保护
- 冷却窗口：单次调整后 120s 内不再调整。
- 步长限制：每次最多调整 `max(1, floor(core*0.1))`。
- 最小变更阈值：`deltaCoreMin`、`deltaQueueMin`。
- 熔断：连续 M 次 OOM 预警或高 GC，强制切换到保守配置。
- 回退：支持动态开关，秒级回退到静态配置（A）。

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
      step-ratio: 0.1
      delta-core-min: 2
      delta-queue-min: 200
      consecutive-up-windows: 3
      consecutive-down-windows: 5

    guard:
      heap-guard-low: 0.75
      heap-guard-high: 0.82
      target-queue-wait-ms: 100
      queue-wait-recover-ms: 60
      gc-pause-p95-ms: 200
      busy-thread-ratio-threshold: 0.85
```

### 6.4 实现建议（Spring）
- 封装 `AdaptiveThreadPoolManager`：负责采集指标、计算目标值、执行调整。
- 使用可动态变更队列容量的实现（如自定义 `ResizableCapacityLinkedBlockingQueue`）。
- 线程池调整通过 `ThreadPoolExecutor#setCorePoolSize/setMaximumPoolSize`。
- 指标接入 Micrometer + Prometheus，关键告警接入 Alertmanager。
- 增加“只读推荐模式”（shadow mode），先观测再执行。

### 6.5 伪代码（含扩容闸门）

```java
void reconcile() {
    Metrics m = metricsCollector.snapshot();
    Budget b = budgetCalculator.recalculateIfNeeded(xmx(), replicaCount());

    int currentCore = executor.getCorePoolSize();
    int targetCore = currentCore;

    if (m.heapUsedRatio > cfg.heapGuardHigh || m.gcPauseP95Ms > cfg.gcPauseThreshold) {
        targetCore = currentCore - stepDown(currentCore);
    } else if (scaleUpGateOpen(m)) {
        targetCore = currentCore + stepUp(currentCore);
    } else if (scaleDownGateOpen(m)) {
        targetCore = currentCore - stepDown(currentCore);
    }

    targetCore = clamp(b.coreMin, targetCore, b.coreMax);
    int targetQueue = clamp(b.queueMin, queueByCore(targetCore), b.queueMax);

    if (cooldownPassed()
            && changedEnough(targetCore, targetQueue)
            && scaleRateWithinLimit(targetCore, targetQueue)) {
        apply(targetCore, targetQueue);
    }
}
```

## 7. 落地路线图

1. Phase 1（1~2 周）：上线方案 A（规则驱动），默认保守参数；打通指标与动态配置开关。
2. Phase 2（2~4 周）：引入方案 C 控制层，先只读（不执行），验证扩容闸门准确性。
3. Phase 3（1~2 周）：灰度开启自动调整（10% -> 30% -> 100%），保留一键回退 A。
4. Phase 4（持续）：基于线上数据微调连续窗口、deadband、步长参数，评估是否需要 D。

## 8. 风险与应对

- 风险：控制振荡导致延迟抖动。
  - 应对：冷却窗口 + 步长限制 + 双阈值迟滞 + 连续窗口。

- 风险：扩容过敏导致频繁变更。
  - 应对：扩容闸门 + deadband + 只读推荐模式。

- 风险：任务内存估计不准引起 OOM。
  - 应对：使用 P95/P99 估计；保留 20% 安全余量；高水位强制降配。

- 风险：副本数感知延迟。
  - 应对：使用 K8s API + 本地缓存 + 超时回退默认值。

- 风险：队列动态缩容行为不安全。
  - 应对：只允许扩容实时生效，缩容通过“延迟缩容或新建池平滑切换”完成。

## 9. 验收标准

- 在同等流量下，`queueWaitP95Ms` 相比静态配置下降 >= 20%。
- 无新增 OOM，`heapUsedRatio > 0.9` 的持续时长下降 >= 30%。
- 拒绝率在目标区间内（如 < 0.1%）。
- 扩缩容行为满足“低频、可解释、可回放”：
  - 每小时调整次数不超过预设上限；
  - 90% 以上扩容事件满足“连续窗口触发”。
- 扩缩容后 5 分钟内恢复到目标 SLO。

## 10. 结论

- 短期可先落地方案 A，快速获得安全收益，并将“扩容闸门”作为统一基础能力。
- 中长期建议采用方案 C（两层混合）作为生产标准方案：
  - 预算层确保不会越过内存与副本约束；
  - 控制层在严格闸门下“谨慎扩容、优先稳定”。
- 方案 B/D 仅在监控和治理能力成熟后逐步引入，避免过度敏感带来系统不稳定。
