package com.stms.query.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class OrderBookVO {

    private String securityId;

    private List<String> lowestAsks;  // 卖价最低5条（有序，从低到高）

    private List<String> highestBids; // 买价最高5条（有序，从高到低）
}
