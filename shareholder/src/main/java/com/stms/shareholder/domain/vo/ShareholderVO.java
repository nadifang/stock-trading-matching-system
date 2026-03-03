package com.stms.shareholder.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShareholderVO {


    /**
     * 订单交易市场
     */
    private String market;

    /**
     * 订单交易股票代码
     */
    private String securityId;

    /**
     * 订单买卖方向
     */
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
     * 订单状态
     */
    private String status;

    /**
     * 已撤订单累计总成交数量
     */
    private Long cumQty;

    /**
     * 已撤订单已撤数量
     */
    private Long canceledQty;

    /**
     * 剩余订单数量
     */
    private Long remainingQty;

    /**
     * 累计成交金额
     */
    private double tradedAmount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
