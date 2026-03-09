package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.impl.Cache2;
import com.zhiwu.project2026.cachereload.impl.Cache3;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:30
 */
@Component
public class TaskCacheInit2 {
    Cache2 pollerCache = new Cache2((key) -> initCache());

    @PostConstruct
    public void init() {

    }

    public void initCache() {
        pollerCache.init();
    }
}
