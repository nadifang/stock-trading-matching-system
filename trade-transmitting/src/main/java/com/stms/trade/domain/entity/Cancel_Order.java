package com.stms.trade.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("cancel_order")
public class Cancel_Order {
    /**
     * 主键
     */
    @TableId(value = "id" , type = IdType.AUTO)
    private Long id;

    /**
     * 撤单唯一编号
     */
    @TableField("cl_order_Id")
    private String clOrderId;

    /**
     * 待撤单订单唯一编号
     */
    @TableField("orig_cl_order_id")
    private String origClOrderId;

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
     * 已撤订单累计总成交数量
     */
    @TableField("cum_qty")
    private Long cumQty;

    /**
     * 已撤订单已撤数量
     */
    @TableField("canceled_qty")
    private Long canceledQty;

    /**
     * 剩余数量
     */
    private String cancelTime;

}
