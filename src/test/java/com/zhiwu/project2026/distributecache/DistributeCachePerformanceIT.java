package com.zhiwu.project2026.distributecache;

import com.zhiwu.project2026.distributecache.cache.CacheKeyBuilder;
import com.zhiwu.project2026.distributecache.model.MeasObject;
import com.zhiwu.project2026.distributecache.repository.MeasObjectDataRepository;
import com.zhiwu.project2026.distributecache.repository.RedisMeasRepository;
import com.zhiwu.project2026.distributecache.service.MeasObjectQueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
    "distributecache.repo.redis.type=redis",
    "distributecache.repo.db.type=jdbc"
})
@TestMethodOrder(MethodOrderer.MethodName.class)
class DistributeCachePerformanceIT {

    private static final Path PERF_REPORT_PATH = Path.of("target", "perf", "distributecache-perf-summary.md");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String token;
    private static String dn;
    private static String taskKey;
    private static String moType;
    private static String originalValuePrefix;
    private static final List<Integer> redisSeedOids = new ArrayList<>();
    private static final List<Integer> dbSeedOids = new ArrayList<>();

    @Autowired
    private RedisMeasRepository redisRepository;

    @Autowired
    private MeasObjectDataRepository dataRepository;

    @Autowired
    private MeasObjectQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheKeyBuilder keyBuilder;

    @BeforeAll
    static void initSeed() {
        token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        dn = "perf-dn-" + token;
        taskKey = "perf-task-" + token;
        moType = "perf-mo";
        originalValuePrefix = "perf-orig-" + token + "-";
    }

    @AfterAll
    static void finish() throws IOException {
        if (!Files.exists(PERF_REPORT_PATH.getParent())) {
            Files.createDirectories(PERF_REPORT_PATH.getParent());
        }
        if (!Files.exists(PERF_REPORT_PATH)) {
            Files.writeString(PERF_REPORT_PATH, "# Distribute Cache Performance Summary\n", StandardOpenOption.CREATE);
        }
    }

    @Test
    void perf01_seedData() {
        Assumptions.assumeTrue(redisAvailable(), "Redis is not reachable from current environment.");

        redisSeedOids.clear();
        Map<Integer, MeasObject> redisObjects = new java.util.LinkedHashMap<>();
        Map<Integer, String> redisOidDn = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            int oid = 90000000 + i;
            redisSeedOids.add(oid);
            MeasObject obj = new MeasObject(oid, dn, originalValuePrefix + i, "zh-r-" + i, "en-r-" + i);
            redisObjects.put(oid, obj);
            redisOidDn.put(oid, dn);
        }
        redisRepository.saveObjects(redisObjects, Duration.ofMinutes(20));
        redisRepository.saveTaskOids(taskKey, moType, redisSeedOids, Duration.ofMinutes(20));
        redisRepository.saveDnOids(dn, redisSeedOids, Duration.ofMinutes(20));
        redisRepository.saveOidDn(redisOidDn, Duration.ofMinutes(20));

        if (mysqlSchemaReady()) {
            for (int i = 0; i < 300; i++) {
                String ov = originalValuePrefix + i;
                jdbcTemplate.update(
                    "INSERT INTO meas_object(dn, original_value, display_value_zh, display_value_en) VALUES(?,?,?,?)",
                    dn, ov, "zh-" + i, "en-" + i
                );
            }
            List<Integer> oids = jdbcTemplate.queryForList(
                "SELECT oid FROM meas_object WHERE dn = ? ORDER BY oid",
                Integer.class, dn
            );
            dbSeedOids.clear();
            dbSeedOids.addAll(oids);
            for (Integer oid : dbSeedOids) {
                jdbcTemplate.update(
                    "INSERT INTO task_oid_binding(task_key, mo_type, oid) VALUES(?,?,?)",
                    taskKey, moType, oid
                );
            }
        }
        Assertions.assertFalse(redisSeedOids.isEmpty());
    }

    @Test
    void perf02_redisRepositoryRead() throws IOException {
        Assumptions.assumeTrue(redisAvailable(), "Redis is not reachable from current environment.");
        Assumptions.assumeTrue(!redisSeedOids.isEmpty(), "Seed data is empty.");

        List<Integer> sample = redisSeedOids.subList(0, Math.min(100, redisSeedOids.size()));
        runWarmup(() -> redisRepository.getObjectsByOids(sample), 30);

        PerfStat stat = runPerf(() -> redisRepository.getObjectsByOids(sample), 500);
        appendReport("Redis getObjectsByOids(100)", stat);
    }

    @Test
    void perf03_jdbcRepositoryRead() throws IOException {
        Assumptions.assumeTrue(mysqlSchemaReady(), "MySQL schema is not reachable from current environment.");
        Assumptions.assumeTrue(!dbSeedOids.isEmpty(), "DB seed data is empty.");

        List<Integer> sample = dbSeedOids.subList(0, Math.min(100, dbSeedOids.size()));
        runWarmup(() -> dataRepository.findObjectsByOids(sample), 10);

        PerfStat stat = runPerf(() -> dataRepository.findObjectsByOids(sample), 120);
        appendReport("JDBC findObjectsByOids(100)", stat);
    }

    @Test
    void perf04_queryServiceReadByTask() throws IOException {
        Assumptions.assumeTrue(mysqlSchemaReady(), "MySQL schema is not reachable from current environment.");
        Assumptions.assumeTrue(redisAvailable(), "Redis is not reachable from current environment.");
        Assumptions.assumeTrue(!dbSeedOids.isEmpty(), "DB seed data is empty.");

        runWarmup(() -> queryService.queryByTask(taskKey, moType), 20);
        PerfStat stat = runPerf(() -> queryService.queryByTask(taskKey, moType), 300);
        appendReport("QueryService queryByTask(300oids)", stat);
    }

    @Test
    void perf99_cleanup() {
        if (taskKey != null || moType != null) {
            redisTemplate.delete(keyBuilder.taskOids(taskKey, moType));
        }
        if (dn != null) {
            redisTemplate.delete(keyBuilder.dnOids(dn));
        }
        for (Integer oid : new HashSet<>(redisSeedOids)) {
            redisTemplate.delete(keyBuilder.measObjByOid(oid));
            redisTemplate.delete(keyBuilder.oidDn(oid));
        }
        if (mysqlSchemaReady()) {
            if (taskKey != null && moType != null) {
                jdbcTemplate.update("DELETE FROM task_oid_binding WHERE task_key = ? AND mo_type = ?", taskKey, moType);
            }
            if (dn != null) {
                jdbcTemplate.update("DELETE FROM meas_object WHERE dn = ?", dn);
            }
        }
        redisSeedOids.clear();
        dbSeedOids.clear();
    }

    private void runWarmup(Runnable action, int count) {
        for (int i = 0; i < count; i++) {
            action.run();
        }
    }

    private PerfStat runPerf(Runnable action, int iterations) {
        List<Long> latenciesMicros = new ArrayList<>(iterations);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long t1 = System.nanoTime();
            action.run();
            long t2 = System.nanoTime();
            latenciesMicros.add((t2 - t1) / 1000);
        }
        long end = System.nanoTime();
        return PerfStat.from(latenciesMicros, iterations, (end - start) / 1_000_000_000.0);
    }

    private void appendReport(String scenario, PerfStat stat) throws IOException {
        if (!Files.exists(PERF_REPORT_PATH.getParent())) {
            Files.createDirectories(PERF_REPORT_PATH.getParent());
        }
        String text = ""
            + "\n## " + scenario + "\n"
            + "- Timestamp: " + LocalDateTime.now().format(TS_FMT) + "\n"
            + "- Iterations: " + stat.iterations + "\n"
            + "- Avg(ms): " + stat.avgMs + "\n"
            + "- P50(ms): " + stat.p50Ms + "\n"
            + "- P95(ms): " + stat.p95Ms + "\n"
            + "- P99(ms): " + stat.p99Ms + "\n"
            + "- Max(ms): " + stat.maxMs + "\n"
            + "- QPS: " + stat.qps + "\n";
        Files.writeString(
            PERF_REPORT_PATH,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private boolean mysqlAvailable() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean mysqlSchemaReady() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM meas_object", Integer.class);
            return one != null && one == 1 && cnt != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean redisAvailable() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return pong != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static final class PerfStat {
        private final int iterations;
        private final double avgMs;
        private final double p50Ms;
        private final double p95Ms;
        private final double p99Ms;
        private final double maxMs;
        private final double qps;

        private PerfStat(int iterations, double avgMs, double p50Ms, double p95Ms, double p99Ms, double maxMs, double qps) {
            this.iterations = iterations;
            this.avgMs = avgMs;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.maxMs = maxMs;
            this.qps = qps;
        }

        private static PerfStat from(List<Long> micros, int iterations, double totalSeconds) {
            if (micros == null || micros.isEmpty()) {
                return new PerfStat(iterations, 0, 0, 0, 0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(micros);
            sorted.sort(Comparator.naturalOrder());
            double avg = sorted.stream().mapToLong(v -> v).average().orElse(0.0) / 1000.0;
            double p50 = percentile(sorted, 50) / 1000.0;
            double p95 = percentile(sorted, 95) / 1000.0;
            double p99 = percentile(sorted, 99) / 1000.0;
            double max = sorted.get(sorted.size() - 1) / 1000.0;
            double qps = totalSeconds <= 0 ? 0 : iterations / totalSeconds;
            return new PerfStat(iterations, round(avg), round(p50), round(p95), round(p99), round(max), round(qps));
        }

        private static double percentile(List<Long> sorted, int p) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }

        private static double round(double v) {
            return Math.round(v * 1000.0) / 1000.0;
        }
    }
}
