package com.zhiwu.project2026.cachereload;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:31
 */
@Service
@Log4j2
public class CacheManager {

    @PostConstruct
    public void init() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                CacheMgr.reload();
            } catch (Throwable e) {
                log.error(e);
            }
        }, 0, 1, TimeUnit.HOURS);
    }
}
