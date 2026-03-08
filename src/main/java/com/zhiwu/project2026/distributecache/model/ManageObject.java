package com.zhiwu.project2026.distributecache.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2026/3/8 18:11
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ManageObject {
    private String moType;

    private List<String> dns;
}
