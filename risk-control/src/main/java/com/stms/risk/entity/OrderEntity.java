package com.stms.risk.entity;

import lombok.Data;

@Data
public class OrderEntity {
    private Long id;
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;          // BUY 或 SELL
    private Long qty;
    private Double price;
    private String shareholderId;
    private String status;        // NEW, PARTIAL 等
}