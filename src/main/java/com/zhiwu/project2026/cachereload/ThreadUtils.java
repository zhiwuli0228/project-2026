package com.zhiwu.project2026.cachereload;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:03
 */
@Log4j2
public class ThreadUtils {
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("sleep error", e);
        }
    }
}
