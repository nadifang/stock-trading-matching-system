package com.stms.common.constant;

/**
 * 订单合法化失败,失败原因常量
 */
public class OrderIllegalReasonConstant {
    public static final String ORDER_IS_NULL_REASON = "订单为空";

    public static final String ORDER_CL_ORDER_ID_ILLEGAL_REASON = "订单ID必须为16位";

    public static final String MARKET_CODE_ILLEGAL_REASON = "市场代码非法";

    public static final String SECURITY_ID_ILLEGAL_REASON = "股票代码必须为6位";

    public static final String SIDE_ILLEGAL_REASON = "买卖方向必须为 B 或 S";

    public static final String QTY_ILLEGAL_REASON = "数量必须大于0";

    public static final String PRICE_ILLEGAL_REASON = "价格必须大于0";

    public static final String SHAREHOLDER_ID_ILLEGAL_REASON = "股东号必须为10位";

    public static final String ORDER_EXISTED_REASON = "订单重复创建";

    public static final String SYSTEM_IS_BUSY_REASON = "系统繁忙，请重试";

    public static final String ORDER_DUPLICATE_REQUEST_REASON = "订单重复创建";
}
