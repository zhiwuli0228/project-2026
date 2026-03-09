package com.zhiwu.project2026.cachereload.bo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/9 21:09
 */
@Getter
@Setter
public class Poller {
    private String key;

    private String type;

    private List<String> meaning;
    private List<String> meaning2;
    private List<String> meaning3;

}
