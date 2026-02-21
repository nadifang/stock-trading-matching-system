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

    // 手动将前端传的 String 转为 char[]
    public void setClOrderId(String clOrderId) {
        this.clOrderId = clOrderId != null ? clOrderId.toCharArray() : null;
    }

    public void setMarket(String market) {
        this.market = market != null ? market.toCharArray() : null;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId != null ? securityId.toCharArray() : null;
    }

    public void setSide(String side) {
        this.side = side != null ? side.toCharArray() : null;
    }

    public void setShareholderId(String shareholderId) {
        this.shareholderId = shareholderId != null ? shareholderId.toCharArray() : null;
    }

}
