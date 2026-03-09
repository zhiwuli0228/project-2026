package com.zhiwu.project2026.cachereload.impl;

import com.zhiwu.project2026.cachereload.LocalCache;
import com.zhiwu.project2026.cachereload.bo.Poller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:25
 */
public class Cache2 extends LocalCache<String, Object> {
    public Cache2(Consumer consumer) {
        super(consumer);
    }

    public void init() {
        // 查询数据库

    }
}
