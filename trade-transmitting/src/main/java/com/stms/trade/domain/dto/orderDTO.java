package com.stms.trade.domain.dto;

import lombok.Data;

@Data
public class orderDTO {
    private char[] clOrderId;                           //订单唯一编号
    private char[] market;                              //订单交易市场
    private char[] securityId;                          //订单交易股票代码
    private char[] side;                                //订单买卖方向
    private Long qty;                                   //订单数量
    private double price;                               //价格
    private char[] shareholderId;                       //股东ID
}
