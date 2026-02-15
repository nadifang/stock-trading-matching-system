package com.stms.trade_return.domain.VO;

import com.stms.trade_return.Utils.TradeReturnCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeReturnVO {
    private char[] clOrderId;                           //订单唯一编号
    private char[] market;                              //订单交易市场
    private char[] securityId;                          //订单交易股票代码
    private char[] side;                                //订单买卖方向
    private Long qty;                                   //订单数量
    private double price;                               //价格
    private char[] shareholderId;                       //股东ID

    /**
     * 成交的唯一编号
     */
    private char[] execId;

    /**
     * 成交数量
     */
    private Long execQty;

    /**
     * 成交价格
     */
    private double execPrice;

    /**
     * 待撤订单的唯一编号
     */
    private char[] origClOrderId;

    /**
     * 订单非法回报，非法代码
     */
    private Integer rejectCode;

    /**
     * 订单非法回报，非法原因说明
     */
    private char[] rejectText;

}
