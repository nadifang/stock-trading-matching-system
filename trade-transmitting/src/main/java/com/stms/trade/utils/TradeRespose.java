package com.stms.trade.utils;

import com.stms.trade.domain.vo.errorMsg;
import com.stms.trade.domain.vo.orderVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeRespose {

    private String clOrderId;                           //订单唯一编号
    private String market;                              //订单交易市场
    private String securityId;                          //订单交易股票代码
    private String side;                                //订单买卖方向
    private Long qty;                                   //订单数量
    private double price;                               //价格
    private String shareholderId;                       //股东ID
    private boolean success;

    /**
     * 订单非法回报，非法代码
     */
    private Integer rejectCode;

    /**
     * 订单非法回报，非法原因说明
     */
    private String rejectText;

    public static TradeRespose fail(Integer rejectCode, String rejectText) {
        TradeRespose respose = new TradeRespose();
        respose.success = false;
        respose.rejectCode = rejectCode;
        respose.rejectText = rejectText;
        return respose;
    }
    public static TradeRespose success() {
        TradeRespose respose = new TradeRespose();
        respose.success = true;
        return respose;
    }


    public Object getData() {
        char[] ClOrderId = clOrderId != null ? clOrderId.toCharArray() : null;
        char[] Market = market != null ? market.toCharArray() : null;
        char[] SecurityId = securityId != null ? securityId.toCharArray() : null;
        char[] Side = side != null ? side.toCharArray() : null;
        char[] ShareholderId = shareholderId != null ? shareholderId.toCharArray() : null;
        return new orderVO(ClOrderId, Market, SecurityId, Side, qty, price, ShareholderId);
    }

    public Object getErrorMsg() {
        char[] RejectText = rejectText != null ? rejectText.toCharArray() : null;
        return new errorMsg(rejectCode, RejectText);
    }
}
