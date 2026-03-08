package com.zhiwu.project2026.distributecache;

import com.zhiwu.project2026.distributecache.model.MeasObject;
import com.zhiwu.project2026.distributecache.repository.MeasObjectDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
    "distributecache.repo.redis.type=inmemory",
    "distributecache.repo.db.type=jdbc"
})
class JdbcMeasObjectDataRepositoryIT {

    @Autowired
    private MeasObjectDataRepository dataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private String dn;
    private String moType;
    private String taskKey;
    private String originalValue;
    private Integer oid;

    @BeforeEach
    void setup() {
        Assumptions.assumeTrue(mysqlAvailable(), "MySQL is not reachable from current environment.");

        token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        dn = "it-dn-" + token;
        moType = "it-type";
        taskKey = "it-task-" + token;
        originalValue = "it-orig-" + token;

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
        }
    }

    @Test
    void shouldQueryByJdbcRepositoryMethods() {
        Assertions.assertNotNull(oid);

        List<Integer> taskOids = dataRepository.findTaskOids(taskKey, moType);
        Assertions.assertEquals(List.of(oid), taskOids);

        List<Integer> dnOids = dataRepository.findDnOids(dn);
        Assertions.assertTrue(dnOids.contains(oid));

        Map<Integer, String> dnMap = dataRepository.findDnByOids(List.of(oid));
        Assertions.assertEquals(dn, dnMap.get(oid));

        Map<Integer, MeasObject> objectMap = dataRepository.findObjectsByOids(List.of(oid));
        Assertions.assertEquals(originalValue, objectMap.get(oid).getOriginalValue());

        List<Integer> originalOids = dataRepository.findOidsByOriginalValue(originalValue, 0, 10);
        Assertions.assertTrue(originalOids.contains(oid));
    }

    private boolean mysqlAvailable() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            return false;
        }
    }
}
