package com.zhiwu.project2026.distributecache.repository;

import com.zhiwu.project2026.distributecache.model.MeasObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "distributecache.repo.db", name = "type", havingValue = "jdbc")
public class JdbcMeasObjectDataRepository implements MeasObjectDataRepository {

    private static final RowMapper<MeasObject> MEAS_OBJECT_ROW_MAPPER = (rs, rowNum) -> {
        MeasObject object = new MeasObject();
        object.setOid(rs.getInt("oid"));
        object.setDn(rs.getString("dn"));
        object.setOriginalValue(rs.getString("original_value"));
        object.setDisplayValueZh(rs.getString("display_value_zh"));
        object.setDisplayValueEn(rs.getString("display_value_en"));
        return object;
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcMeasObjectDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<Integer, MeasObject> findObjectsByOids(Collection<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyMap();
        }
        String inClause = inClause(oids.size());
        String sql = "SELECT oid, dn, original_value, display_value_zh, display_value_en " +
            "FROM meas_object WHERE oid IN (" + inClause + ")";
        List<MeasObject> rows = jdbcTemplate.query(sql, MEAS_OBJECT_ROW_MAPPER, oids.toArray());
        Map<Integer, MeasObject> result = new HashMap<>();
        for (MeasObject row : rows) {
            result.put(row.getOid(), row);
        }
        return result;
    }

    @Override
    public List<Integer> findTaskOids(String taskKey, String moType) {
        String sql = "SELECT oid FROM task_oid_binding WHERE task_key = ? AND mo_type = ? ORDER BY oid";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("oid"), taskKey, moType);
    }

    @Override
    public Map<Integer, String> findDnByOids(Collection<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyMap();
        }
        String inClause = inClause(oids.size());
        String sql = "SELECT oid, dn FROM meas_object WHERE oid IN (" + inClause + ")";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, oids.toArray());
        Map<Integer, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Number oid = (Number) row.get("oid");
            String dn = (String) row.get("dn");
            if (oid != null && dn != null) {
                result.put(oid.intValue(), dn);
            }
        }
        return result;
    }

    @Override
    public List<Integer> findDnOids(String dn) {
        String sql = "SELECT oid FROM meas_object WHERE dn = ? ORDER BY oid";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("oid"), dn);
    }

    @Override
    public List<Integer> findOidsByOriginalValue(String originalValue, int offset, int limit) {
        String sql = "SELECT oid FROM meas_object WHERE original_value = ? ORDER BY oid LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("oid"), originalValue, limit, offset);
    }

    private String inClause(int size) {
        if (size <= 0) {
            return "NULL";
        }
        return String.join(",", Collections.nCopies(size, "?"));
    }
}

