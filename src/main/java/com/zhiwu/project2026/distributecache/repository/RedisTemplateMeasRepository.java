package com.zhiwu.project2026.distributecache.repository;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.zhiwu.project2026.distributecache.cache.CacheKeyBuilder;
import com.zhiwu.project2026.distributecache.model.MeasObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "distributecache.repo.redis", name = "type", havingValue = "redis")
public class RedisTemplateMeasRepository implements RedisMeasRepository {

    private static final TypeReference<List<Integer>> INT_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    public RedisTemplateMeasRepository(StringRedisTemplate redisTemplate, CacheKeyBuilder keyBuilder) {
        this.redisTemplate = redisTemplate;
        this.keyBuilder = keyBuilder;
    }

    @Override
    public Map<Integer, MeasObject> getObjectsByOids(Collection<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> keyByOid = new LinkedHashMap<>();
        for (Integer oid : oids) {
            if (oid != null) {
                keyByOid.put(oid, keyBuilder.measObjByOid(oid));
            }
        }
        List<String> values = redisTemplate.opsForValue().multiGet(new ArrayList<>(keyByOid.values()));
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, MeasObject> result = new HashMap<>();
        int index = 0;
        for (Integer oid : keyByOid.keySet()) {
            if (index >= values.size()) {
                break;
            }
            String payload = values.get(index++);
            if (payload != null && !payload.isBlank()) {
                result.put(oid, JSON.parseObject(payload, MeasObject.class));
            }
        }
        return result;
    }

    @Override
    public List<Integer> getTaskOids(String taskKey, String moType) {
        String payload = redisTemplate.opsForValue().get(keyBuilder.taskOids(taskKey, moType));
        if (payload == null || payload.isBlank()) {
            return Collections.emptyList();
        }
        List<Integer> result = JSON.parseObject(payload, INT_LIST_TYPE);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public Map<Integer, String> getDnByOids(Collection<Integer> oids) {
        if (oids == null || oids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> keyByOid = new LinkedHashMap<>();
        for (Integer oid : oids) {
            if (oid != null) {
                keyByOid.put(oid, keyBuilder.oidDn(oid));
            }
        }
        List<String> values = redisTemplate.opsForValue().multiGet(new ArrayList<>(keyByOid.values()));
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> result = new HashMap<>();
        int index = 0;
        for (Integer oid : keyByOid.keySet()) {
            if (index >= values.size()) {
                break;
            }
            String dn = values.get(index++);
            if (dn != null && !dn.isBlank()) {
                result.put(oid, dn);
            }
        }
        return result;
    }

    @Override
    public List<Integer> getDnOids(String dn) {
        String payload = redisTemplate.opsForValue().get(keyBuilder.dnOids(dn));
        if (payload == null || payload.isBlank()) {
            return Collections.emptyList();
        }
        List<Integer> result = JSON.parseObject(payload, INT_LIST_TYPE);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public void saveObjects(Map<Integer, MeasObject> objects, Duration ttl) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, MeasObject> entry : objects.entrySet()) {
            Integer oid = entry.getKey();
            MeasObject value = entry.getValue();
            if (oid == null || value == null) {
                continue;
            }
            redisTemplate.opsForValue().set(keyBuilder.measObjByOid(oid), JSON.toJSONString(value), safeTtl(ttl));
        }
    }

    @Override
    public void saveTaskOids(String taskKey, String moType, List<Integer> oids, Duration ttl) {
        redisTemplate.opsForValue().set(
            keyBuilder.taskOids(taskKey, moType),
            JSON.toJSONString(Objects.requireNonNullElse(oids, Collections.emptyList())),
            safeTtl(ttl)
        );
    }

    @Override
    public void saveOidDn(Map<Integer, String> oidDnMap, Duration ttl) {
        if (oidDnMap == null || oidDnMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, String> entry : oidDnMap.entrySet()) {
            Integer oid = entry.getKey();
            String dn = entry.getValue();
            if (oid == null || dn == null) {
                continue;
            }
            redisTemplate.opsForValue().set(keyBuilder.oidDn(oid), dn, safeTtl(ttl));
        }
    }

    @Override
    public void saveDnOids(String dn, List<Integer> oids, Duration ttl) {
        redisTemplate.opsForValue().set(
            keyBuilder.dnOids(dn),
            JSON.toJSONString(Objects.requireNonNullElse(oids, Collections.emptyList())),
            safeTtl(ttl)
        );
    }

    private Duration safeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofMinutes(10);
        }
        return ttl;
    }
}

