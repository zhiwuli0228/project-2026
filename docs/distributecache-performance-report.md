# DistributeCache 性能测试报告（最终版）

## 1. 测试时间
- 2026-03-08 23:47 ~ 23:48 (Asia/Shanghai)

## 2. 测试环境
- 应用：Spring Boot 3.5.4
- 缓存：Redis Cluster（外部服务）
- 数据库：MySQL（外部服务）
- 测试类：`DistributeCachePerformanceIT`

## 3. 测试命令
```bash
mvn -q "-Dtest=DistributeCachePerformanceIT" test
```

## 4. 测试结果摘要
- 总用例：5
- 通过：5
- 跳过：0
- 失败：0

Surefire 结果：
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

报告文件：
- `target/surefire-reports/com.zhiwu.project2026.distributecache.DistributeCachePerformanceIT.txt`
- `target/perf/distributecache-perf-summary.md`

## 5. 关键性能结果（本轮）

### 5.1 Redis getObjectsByOids(100)
- Iterations: 500
- Avg: 32.611 ms
- P50: 21.493 ms
- P95: 77.201 ms
- P99: 85.837 ms
- Max: 280.710 ms
- QPS: 30.661

### 5.2 JDBC findObjectsByOids(100)
- Iterations: 120
- Avg: 14.729 ms
- P50: 10.751 ms
- P95: 19.554 ms
- P99: 79.384 ms
- Max: 265.437 ms
- QPS: 67.881

### 5.3 QueryService queryByTask(300oids)
- Iterations: 300
- Avg: 0.068 ms
- P50: 0.047 ms
- P95: 0.118 ms
- P99: 0.144 ms
- Max: 0.188 ms
- QPS: 14581.085

## 6. 结果解读
1. Redis 读取路径在 100 对象批量场景下 P99 为 85.837ms，整体稳定。
2. JDBC 直查路径 P95 为 19.554ms，P99 存在长尾（79.384ms），建议继续观察网络抖动与连接池参数。
3. QueryService 在当前压测场景下性能极高，说明 L1/L2 命中路径生效明显。

## 7. 已完成修复
1. 自动建库：JDBC URL 增加 `createDatabaseIfNotExist=true`。
2. 自动建表：启动时自动创建 `meas_object` 与 `task_oid_binding`。
3. 索引长度兼容修复：`dn`/`original_value` 调整为 `VARCHAR(255)`，避免索引长度超限。

## 8. 建议下一步
1. 增加并发性能测试（并发 10/50/100）对比 P95/P99。
2. 增加写入链路压测（UPSERT/DELETE 事件消费 + 缓存失效）。
3. 将本测试纳入 CI 定时任务，持续追踪性能回归。
