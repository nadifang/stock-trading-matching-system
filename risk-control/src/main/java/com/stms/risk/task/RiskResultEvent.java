package com.stms.risk.task;

import lombok.Data;

@Data
public class RiskResultEvent {
    private String clOrderId;
    private boolean riskPassed;  // true=通过，false=拒绝
    private String message;      // 附加信息
}