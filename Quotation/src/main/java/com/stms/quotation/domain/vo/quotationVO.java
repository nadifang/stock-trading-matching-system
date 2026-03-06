package com.stms.quotation.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class quotationVO {

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
