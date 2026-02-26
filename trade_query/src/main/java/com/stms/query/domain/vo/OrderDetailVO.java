package com.stms.query.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailVO {

    private String clOrderId;

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
     * 价格
     */
    private double price;

    /**
     * 股东ID
     */
    private String shareholderId;

    /**
     * 剩余订单数量
     */
    private Long remainingQty;

    public OrderDetailVO(OrderDetailVO orderById) {
        this.clOrderId = orderById.getClOrderId();
        this.market = orderById.getMarket();
        this.securityId = orderById.getSecurityId();
        this.side = orderById.getSide();
        this.price = orderById.getPrice();
        this.shareholderId = orderById.getShareholderId();
        this.remainingQty = orderById.getRemainingQty();
    }
}
