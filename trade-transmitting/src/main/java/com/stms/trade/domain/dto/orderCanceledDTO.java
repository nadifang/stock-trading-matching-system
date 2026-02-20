package com.stms.trade.domain.dto;

import lombok.Data;

@Data
public class orderCanceledDTO {
    private char[] clOrderId;         // 要撤的订单ID
    private char[] origClOrderId;     // 原始订单ID（如果不同）
    private char[] market;
    private char[] securityId;
    private char[] shareholderId;
    private char[] side;
}
