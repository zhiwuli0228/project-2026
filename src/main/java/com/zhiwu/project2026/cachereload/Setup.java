package com.zhiwu.project2026.cachereload;

import com.zhiwu.project2026.cachereload.bo.Poller;
import com.zhiwu.project2026.cachereload.impl.PollerCache;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:26
 */
@Service
public class Setup {
    PollerCache pollerCache = new PollerCache((key) -> initCache());

    @PostConstruct
    public void init() {

    }

    public void initCache() {
        List<Poller>  pollers = new ArrayList<>();
        // TODO 从数据库中查询，初始化pollers
        pollerCache.init(pollers);
    }
}
