package com.zhiwu.project2026.distributecache;

import com.zhiwu.project2026.distributecache.cache.CacheKeyBuilder;
import com.zhiwu.project2026.distributecache.model.MeasObject;
import com.zhiwu.project2026.distributecache.service.MeasObjectQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

@SpringBootTest(properties = {
    "distributecache.repo.redis.type=redis",
    "distributecache.repo.db.type=jdbc"
})
class DefaultMeasObjectQueryServiceIT {

    @Autowired
    private MeasObjectQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheKeyBuilder keyBuilder;

    private String token;
    private String dn;
    private String originalValue;
    private String taskKey;
    private String moType;
    private Integer oid;

    @BeforeEach
    void setup() {
        Assumptions.assumeTrue(mysqlAvailable(), "MySQL is not reachable from current environment.");
        Assumptions.assumeTrue(redisAvailable(), "Redis cluster is not reachable from current environment.");

        token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        dn = "it-dn-" + token;
        originalValue = "it-orig-" + token;
        taskKey = "it-task-" + token;
        moType = "it-type";

        jdbcTemplate.update(
            "INSERT INTO meas_object(dn, original_value, display_value_zh, display_value_en) VALUES(?,?,?,?)",
            dn, originalValue, "zh-" + token, "en-" + token
        );
        oid = jdbcTemplate.queryForObject(
            "SELECT oid FROM meas_object WHERE dn = ? AND original_value = ?",
            Integer.class, dn, originalValue
        );
        jdbcTemplate.update(
            "INSERT INTO task_oid_binding(task_key, mo_type, oid) VALUES(?,?,?)",
            taskKey, moType, oid
        );
    }

    @AfterEach
    void cleanup() {
        if (taskKey != null && moType != null) {
            jdbcTemplate.update("DELETE FROM task_oid_binding WHERE task_key = ? AND mo_type = ?", taskKey, moType);
        }
        if (dn != null) {
            jdbcTemplate.update("DELETE FROM meas_object WHERE dn = ?", dn);
            redisTemplate.delete(keyBuilder.dnOids(dn));
        }
        if (oid != null) {
            redisTemplate.delete(keyBuilder.measObjByOid(oid));
            redisTemplate.delete(keyBuilder.oidDn(oid));
        }
        if (taskKey != null || moType != null) {
            redisTemplate.delete(keyBuilder.taskOids(taskKey, moType));
        }
    }

    @Test
    void shouldQueryByTaskDnAndOriginalValue() {
        List<MeasObject> byTask = queryService.queryByTask(taskKey, moType);
        Assertions.assertFalse(byTask.isEmpty());
        Assertions.assertEquals(originalValue, byTask.get(0).getOriginalValue());

        List<MeasObject> byDn = queryService.queryByDn(dn);
        Assertions.assertFalse(byDn.isEmpty());

        List<MeasObject> byOriginal = queryService.queryByOriginalValue(originalValue, 1, 10);
        Assertions.assertFalse(byOriginal.isEmpty());
    }

    private boolean mysqlAvailable() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
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
}
