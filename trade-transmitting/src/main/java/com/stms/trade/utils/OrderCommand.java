package com.stms.trade.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommand {
    private String clOrderId;                           //订单唯一编号
    private String origClOrderId;                       //(撤单)原始订单ID（如果不同）
    private String market;                              //订单交易市场
    private String securityId;                          //订单交易股票代码
    private String side;                                //订单买卖方向
    private Long qty;                                   //订单数量
    private double price;                               //价格
    private String shareholderId;                       //股东ID
    private String status;                              //订单状态
}
