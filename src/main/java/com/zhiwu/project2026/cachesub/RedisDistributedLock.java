package com.zhiwu.project2026.cachesub;

import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的简单分布式锁。
 */
@Component
public class RedisDistributedLock {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else "
                    + "return 0 "
                    + "end";

    private final JedisPooled jedisPooled;

    public RedisDistributedLock() {
        this.jedisPooled = new JedisPooled("127.0.0.1", 6379);
    }

    /**
     * 尝试获取锁，只有 key 不存在时才会成功，并携带过期时间避免死锁。
     */
    public String tryLock(String key, long ttlMillis) {
        String value = UUID.randomUUID().toString();
        String result = jedisPooled.set(key, value, SetParams.setParams().nx().px(ttlMillis));
        return "OK".equals(result) ? value : null;
    }

    /**
     * 只允许锁持有者释放锁。
     */
    public boolean unlock(String key, String value) {
        if (value == null) {
            return false;
        }
        Object result = jedisPooled.eval(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                Collections.singletonList(value)
        );
        return Long.valueOf(1L).equals(result);
    }
}
