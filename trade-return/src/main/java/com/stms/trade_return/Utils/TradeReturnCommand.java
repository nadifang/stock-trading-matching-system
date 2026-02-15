package com.stms.trade_return.Utils;

import com.stms.trade_return.domain.VO.TradeReturnVO;
import lombok.Data;

@Data
public class TradeReturnCommand {
    private String clOrderId;                           //订单唯一编号
    private String market;                              //订单交易市场
    private String securityId;                          //订单交易股票代码
    private String side;                                //订单买卖方向
    private Long qty;                                   //订单数量
    private Double price;                               //价格
    private String shareholderId;                       //股东ID

    /**
     * 成交的唯一编号
     */
    private String execId;

    /**
     * 成交数量
     */
    private Long execQty;

    /**
     * 成交价格
     */
    private Double execPrice;

    /**
     * 待撤订单的唯一编号
     */
    private String origClOrderId;

    /**
     * 订单非法回报，非法代码
     */
    private Integer rejectCode;

    /**
     * 订单非法回报，非法原因说明
     */
    private String rejectText;

    public Object fail() {
        char[] ClOrderId = clOrderId != null ? clOrderId.toCharArray() : null;
        char[] OrigClOrderId = origClOrderId != null ? origClOrderId.toCharArray() : null;
        char[] RejectText = rejectText != null ? rejectText.toCharArray() : null;
        return new TradeReturnVO(ClOrderId, null, null, null, null, 0.0, null, null, null, 0.0, OrigClOrderId, rejectCode, RejectText);
    }
    public Object success() {
        char[] ClOrderId = clOrderId != null ? clOrderId.toCharArray() : null;
        char[] Market = market != null ? market.toCharArray() : null;
        char[] SecurityId = securityId != null ? securityId.toCharArray() : null;
        char[] Side = side != null ? side.toCharArray() : null;
        char[] ShareholderId = shareholderId != null ? shareholderId.toCharArray() : null;
        char[] ExecId = execId != null ? execId.toCharArray() : null;
        return new TradeReturnVO(ClOrderId, Market, SecurityId, Side, qty, price, ShareholderId, ExecId, execQty, execPrice, null, null, null);
    }
}
