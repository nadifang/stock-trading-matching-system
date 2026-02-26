package com.stms.common.constant;

public class RedisConstant {

    public static final String ORDER_STATUS_KEY = "order:status:";

    // 空值缓存标记
    public static final String EMPTY_VALUE = null;

    // 空值缓存 TTL（5分钟）
    public static final long EMPTY_CACHE_TTL = 300;

    // 热点订单阈值（1分钟内访问超过此次数视为热点）
    public static final int HOT_ORDER_THRESHOLD = 10;

    // 热点订单 TTL（2小时）
    public static final long HOT_ORDER_TTL = 7200;
}
