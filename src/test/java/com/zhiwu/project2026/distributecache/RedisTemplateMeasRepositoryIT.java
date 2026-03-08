package com.zhiwu.project2026.distributecache;

import com.zhiwu.project2026.distributecache.cache.CacheKeyBuilder;
import com.zhiwu.project2026.distributecache.model.MeasObject;
import com.zhiwu.project2026.distributecache.repository.RedisMeasRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
    "distributecache.repo.redis.type=redis",
    "distributecache.repo.db.type=inmemory"
})
class RedisTemplateMeasRepositoryIT {

    @Autowired
    private RedisMeasRepository redisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheKeyBuilder keyBuilder;

    private int oid1;
    private int oid2;
    private String dn;
    private String taskKey;
    private String moType;

    @AfterEach
    void cleanup() {
        if (oid1 > 0) {
            redisTemplate.delete(keyBuilder.measObjByOid(oid1));
            redisTemplate.delete(keyBuilder.oidDn(oid1));
        }
        if (oid2 > 0) {
            redisTemplate.delete(keyBuilder.measObjByOid(oid2));
            redisTemplate.delete(keyBuilder.oidDn(oid2));
        }
        if (dn != null) {
            redisTemplate.delete(keyBuilder.dnOids(dn));
        }
        if (taskKey != null || moType != null) {
            redisTemplate.delete(keyBuilder.taskOids(taskKey, moType));
        }
    }

    @Test
    void shouldSaveAndQueryRedisRepository() {
        Assumptions.assumeTrue(redisAvailable(), "Redis cluster is not reachable from current environment.");

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        oid1 = 80000001;
        oid2 = 80000002;
        dn = "it-dn-" + token;
        taskKey = "it-task-" + token;
        moType = "it-type";

        MeasObject m1 = new MeasObject(oid1, dn, "orig-a", "zh-a", "en-a");
        MeasObject m2 = new MeasObject(oid2, dn, "orig-b", "zh-b", "en-b");

        redisRepository.saveObjects(Map.of(oid1, m1, oid2, m2), Duration.ofMinutes(10));
        redisRepository.saveOidDn(Map.of(oid1, dn, oid2, dn), Duration.ofMinutes(10));
        redisRepository.saveDnOids(dn, Arrays.asList(oid1, oid2), Duration.ofMinutes(10));
        redisRepository.saveTaskOids(taskKey, moType, Arrays.asList(oid1, oid2), Duration.ofMinutes(10));

        Map<Integer, MeasObject> objectMap = redisRepository.getObjectsByOids(Arrays.asList(oid1, oid2));
        Assertions.assertEquals(2, objectMap.size());
        Assertions.assertEquals(dn, objectMap.get(oid1).getDn());

        List<Integer> taskOids = redisRepository.getTaskOids(taskKey, moType);
        Assertions.assertEquals(List.of(oid1, oid2), taskOids);

        List<Integer> dnOids = redisRepository.getDnOids(dn);
        Assertions.assertEquals(List.of(oid1, oid2), dnOids);

        Map<Integer, String> oidDn = redisRepository.getDnByOids(Arrays.asList(oid1, oid2));
        Assertions.assertEquals(dn, oidDn.get(oid1));
        Assertions.assertEquals(dn, oidDn.get(oid2));
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
