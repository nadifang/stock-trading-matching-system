package com.stms.trade.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_order")
public class Order {

    /**
     * 主键
     */
    @TableId(value = "id" , type = IdType.AUTO)
    private Long id;

    /**
     * 订单唯一编号
     */
    @TableField("clOrder_Id")
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
     * 订单数量
     */
    private Long qty;

    /**
     * 价格
     */
    private double price;

    /**
     * 股东ID
     */
    private String shareholderId;

    /**
     * 订单状态
     */
    private String status;


    public Order(String clOrderId, String market, String securityId, String side, Long qty, double price, String shareholderId) {
        this.clOrderId = clOrderId;
        this.market = market;
        this.securityId = securityId;
        this.side = side;
        this.qty = qty;
        this.price = price;
        this.shareholderId = shareholderId;
    }
}
