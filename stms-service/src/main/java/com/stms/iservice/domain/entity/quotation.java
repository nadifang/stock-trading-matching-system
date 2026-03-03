package com.stms.iservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("quotation")
@Data
public class quotation {

    /**
     * 主键id
     */
    @TableId(value = "id" , type = IdType.AUTO)
    private Long id;

    /**
     * 行情对应的市场
     */
    private String market;

    /**
     * 行情对应的股票代码
     */
    private String securityId;

    /**
     * 行情对应的最新价格
     */
    private double bidPrice;

    /**
     * 行情对应最新的卖价
     */
    private double askPrice;

    /**
     * 行情更新时间
     */
    private LocalDateTime updateTime;
}
