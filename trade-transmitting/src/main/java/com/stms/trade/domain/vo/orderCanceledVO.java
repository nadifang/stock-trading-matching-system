package com.stms.trade.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class orderCanceledVO {

    private char[] clOrderId;         // 要撤的订单ID
    private char[] origClOrderId;     // 原始订单ID（如果不同）
    private char[] market;
    private char[] securityId;
    private char[] shareholderId;
    private char[] side;

    /**
     * 订单数量
     */
    private Long qty;

    /**
     * 价格
     */
    private double price;

    /**
     * 已撤订单累计总成交数量
     */
    private Long cumQty;

    /**
     * 已撤订单已撤数量
     */
    private Long canceledQty;
}
