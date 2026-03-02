package com.stms.export.domian.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Top10_securityIdVO {

    /**
     * 股票代码，用“，”分隔
     */
    private String securityId;

    /**
     * 累计成交数量，用“，”分隔
     */
    private String amount;
}
