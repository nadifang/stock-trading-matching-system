package com.stms.risk.entity;

import lombok.Data;

@Data
public class ShareholderGroup {
    private Long id;
    private String groupId;       // 组ID
    private String shareholderId; // 股东代码
}