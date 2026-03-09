# PollerCache 从 `Set` 改为 `AtomicReference<Set>` 的故障分析

## 结论

可以确认：这次现网问题和 `PollerCache` 数据结构从
`Map<String, Set<Poller>>` 改成
`Map<String, AtomicReference<Set<Poller>>>`
高度相关。

根因不是 `AtomicReference` 本身不安全，而是改造后读写模式发生变化，代码仍沿用旧思路，导致出现新的失败路径。

## 现象对应到代码

### 1. 初始化路径更容易出现空引用/逻辑空装载

当前 `PollerCache` 的缓存类型是：

- [PollerCache.java:24](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/impl/PollerCache.java:24)

初始化时需要先构造 `AtomicReference<Set<Poller>>`，再放入 map：

- [PollerCache.java:30](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/impl/PollerCache.java:30)
- [PollerCache.java:33](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/impl/PollerCache.java:33)

一旦在你之前那版代码里存在以下写法（你现场描述中出现过）：

- `new AtomicReference<>()` 后直接 `ref.get().addAll(...)`

就会触发 NPE（`ref.get()==null`）。

这类问题在旧结构 `Map<String, Set<Poller>>` 下不会出现，因为没有“引用层 + 值层”两层初始化。

### 2. 新增 key 时，写回 map 的要求更严格，容易丢数据

`AtomicReference` 方案下，若使用 `getOrDefault` 取不到 key，就会拿到“临时 ref”。
如果 CAS 成功但没有 `put` 回 map，该 type 的数据会丢失（逻辑上像“初始化失败”）。

当前代码已用 `computeIfAbsent` 避免这个坑：

- [PollerCache.java:47](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/impl/PollerCache.java:47)

旧 `Map<String, Set<Poller>>` 一般直接 `computeIfAbsent(type, ...)` 后 `add`，这个坑不明显。

### 3. 你看到“没打 error 日志”不代表没失败

`LocalCache.reload()` 的日志只在抛异常时打印：

- [LocalCache.java:41](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/LocalCache.java:41)
- [LocalCache.java:42](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/LocalCache.java:42)

因此：

- 若 `consumer` 卡住（DB 阻塞/远程调用阻塞）不会打 error。
- 若 `consumer` 正常返回但逻辑上没把数据放进去，也不会打 error。

所以“没有 `reload cache error`”只能说明“没有抛出到 catch”，不能说明“初始化成功”。

### 4. 写锁只保证串行，不保证本次一定装载成功

写锁位置：

- [LocalCache.java:36](/E:/001code/java/project-2026/src/main/java/com/zhiwu/project2026/cachereload/LocalCache.java:36)

作用仅是同一个 `LocalCache` 实例内串行执行 `consumer`，不负责校验“数据是否装载到目标结构”。
所以在 `AtomicReference` 改造引入逻辑错误时，锁不会阻止“空装载/丢写”。

## 为什么改类型前基本没问题，改后出现问题

改造前（`Map<String, Set<Poller>>`）：

- 只有一层容器，读写路径短，失败模式少。

改造后（`Map<String, AtomicReference<Set<Poller>>>`）：

- 增加了引用层，必须保证：
1. `ref` 本身已创建；
2. `ref.get()` 非空；
3. CAS 的对象来自 map 中同一个 ref；
4. 新 key 必须回写 map。

任一条件没满足，都会出现“初始化看起来执行了，但结果不可用/不完整”。

## 建议（针对你“必须初始化成功”的要求）

1. 保留重试可以，但建议加“限时/限次 + 指标告警”，避免单缓存无限占用调度线程。
2. `init` 成功标准不要只看是否抛异常，增加“装载条数/关键 type 数量”校验。
3. `PollerCache` 增加阶段日志：开始、结束、耗时、装载总数、按 type 数。
4. 所有新增 type 写入统一使用 `computeIfAbsent`，禁止 `getOrDefault + CAS` 模式。
