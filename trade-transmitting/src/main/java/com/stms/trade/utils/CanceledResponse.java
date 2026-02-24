package com.stms.trade.utils;

import com.stms.trade.domain.vo.orderCanceledVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanceledResponse {

    private String clOrderId;         // 要撤的订单ID
    private String origClOrderId;     // 原始订单ID（如果不同）
    private String market;
    private String securityId;
    private String shareholderId;
    private String side;

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

    public CanceledResponse(String clOrderId, String origClOrderId, String market, String securityId, String shareholderId, String side) {
        this.clOrderId = clOrderId;
        this.origClOrderId = origClOrderId;
        this.market = market;
        this.securityId = securityId;
        this.shareholderId = shareholderId;
        this.side = side;
    }

    public Object getData() {
        char[] ClOrderId = clOrderId != null ? clOrderId.toCharArray() : null;
        char[] OrigClOrderId = origClOrderId != null ? origClOrderId.toCharArray() : null;
        char[] Market = market != null ? market.toCharArray() : null;
        char[] SecurityId = securityId != null ? securityId.toCharArray() : null;
        char[] Side = side != null ? side.toCharArray() : null;
        char[] ShareholderId = shareholderId != null ? shareholderId.toCharArray() : null;
        return new orderCanceledVO(ClOrderId, OrigClOrderId, Market, SecurityId, ShareholderId, Side, qty, price, cumQty, canceledQty);
    }
}
