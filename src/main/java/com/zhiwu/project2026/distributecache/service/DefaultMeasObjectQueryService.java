package com.zhiwu.project2026.distributecache.service;

import com.zhiwu.project2026.distributecache.cache.LocalMeasCache;
import com.zhiwu.project2026.distributecache.config.DegradeProperties;
import com.zhiwu.project2026.distributecache.repository.MeasObjectDataRepository;
import com.zhiwu.project2026.distributecache.repository.RedisMeasRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * S1: layered cache baseline strategy.
 */
@Service
@ConditionalOnProperty(prefix = "distributecache.compare", name = "scheme", havingValue = "s1", matchIfMissing = true)
public class DefaultMeasObjectQueryService extends AbstractBaseMeasObjectQueryService {

    public DefaultMeasObjectQueryService(LocalMeasCache localCache,
                                         RedisMeasRepository redisRepository,
                                         MeasObjectDataRepository dataRepository,
                                         DegradeProperties degradeProperties) {
        super(localCache, redisRepository, dataRepository, degradeProperties);
    }
}

