package com.stms.trade.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class errorMsg {
    /**
     * 订单非法回报，非法代码
     */
    private Integer rejectCode;

    /**
     * 订单非法回报，非法原因说明
     */
    private char[] rejectText;
}
