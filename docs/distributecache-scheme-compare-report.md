# DistributeCache 三方案性能对比报告

## 1. 测试说明
- 日期：2026-03-09
- 测试类：`DistributeCachePerformanceIT`
- 数据规模：
  - Redis/JDBC 场景：100 对象批量读取
  - QueryService 场景：task 下 300 oids
- 三套方案通过配置切换：
  - `s1` 分层缓存主方案
  - `s2` 分片驻留方案（模拟 `totalNodes=3,nodeIndex=0`）
  - `s3` 结果页缓存优先方案

## 2. 执行命令
```bash
mvn -q "-Dtest=DistributeCachePerformanceIT" "-Ddistributecache.compare.scheme=s1" test
mvn -q "-Dtest=DistributeCachePerformanceIT" "-Ddistributecache.compare.scheme=s2" "-Ddistributecache.compare.total-nodes=3" "-Ddistributecache.compare.node-index=0" test
mvn -q "-Dtest=DistributeCachePerformanceIT" "-Ddistributecache.compare.scheme=s3" "-Ddistributecache.compare.result-cache-ttl-millis=60000" test
```

## 3. 指标对比

### 3.1 Redis getObjectsByOids(100)
| 方案 | Avg(ms) | P50(ms) | P95(ms) | P99(ms) | QPS |
|---|---:|---:|---:|---:|---:|
| s1 | 33.030 | 22.313 | 79.775 | 89.528 | 30.272 |
| s2 | 32.508 | 27.832 | 102.151 | 114.724 | 30.758 |
| s3 | 32.719 | 28.186 | 109.590 | 120.882 | 30.560 |

### 3.2 JDBC findObjectsByOids(100)
| 方案 | Avg(ms) | P50(ms) | P95(ms) | P99(ms) | QPS |
|---|---:|---:|---:|---:|---:|
| s1 | 14.590 | 8.675 | 44.880 | 48.344 | 68.532 |
| s2 | 14.510 | 9.996 | 41.163 | 54.249 | 68.905 |
| s3 | 15.954 | 15.647 | 16.983 | 23.235 | 62.672 |

### 3.3 QueryService queryByTask(300oids)
| 方案 | Avg(ms) | P50(ms) | P95(ms) | P99(ms) | QPS |
|---|---:|---:|---:|---:|---:|
| s1 | 0.090 | 0.081 | 0.175 | 0.206 | 11023.981 |
| s2 | 108.557 | 96.683 | 167.704 | 181.454 | 9.211 |
| s3 | 0.001 | 0.001 | 0.001 | 0.007 | 641299.701 |

## 4. 结论
1. `s1`：整体均衡稳定，Redis/JDBC/QueryService 指标都在可接受范围，适合作为默认主方案。
2. `s2`：在当前模拟分片配置下，QueryService 明显退化（P99 181.454ms），说明非 owner 路径频繁触发远端缓存/回源，适合特定流量模式，不宜默认启用。
3. `s3`：在重复查询场景下 QueryService 指标最佳（结果缓存命中后接近内存读取），适合前台高重复查询与 SLA 场景。

## 5. 生产建议
1. 默认采用 `s1`。
2. 前台高重复接口按需启用 `s3`（设置合理 TTL，防止脏数据窗口过大）。
3. `s2` 仅在路由亲和性强且可验证 owner 命中率时启用。

## 6. 原始数据
- `target/perf/distributecache-perf-summary.md`
- `target/surefire-reports/com.zhiwu.project2026.distributecache.DistributeCachePerformanceIT.txt`
