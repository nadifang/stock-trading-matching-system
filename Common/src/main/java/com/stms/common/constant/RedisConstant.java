package com.stms.common.constant;

public class RedisConstant {

    public static final String ORDER_STATUS_KEY = "order:status:";

    // 空值缓存标记
    public static final String EMPTY_VALUE = "_EMPTY_";

    // 空值缓存 TTL（5分钟）
    public static final long EMPTY_CACHE_TTL = 300;

    // 热点订单阈值（1分钟内访问超过此次数视为热点）
    public static final int HOT_ORDER_THRESHOLD = 10;

    // 热点订单 TTL（2小时）
    public static final long HOT_ORDER_TTL = 7200;

    // 布隆过滤器 key（简单实现，生产可用 RedisBloom 模块或 Guava）
    public static final String BLOOM_FILTER_KEY = "order:bloom:filter";

    // 空值缓存标记
    public static final String EMPTY_VALUE_STRING = "";

    // 分布式锁过期时间（10秒）
    public static final long LOCK_TTL = 10;

    // 随机TTL范围（0-10分钟），防止缓存雪崩
    public static final int RANDOM_TTL_MINUTES = 10;

    // 布隆过滤器 + 滑动窗口的 key 前缀
    public static final String DUPLICATE_ORDER_BF_KEY = "order:bf:duplicate:";

    // 时间窗口（秒），例如 5 秒内不能重复提交
    public static final int DUPLICATE_WINDOW_SECONDS = 10;

    // 布隆过滤器位图长度（2^20 = 1048576 位，够用且省内存）
    public static final int BF_SIZE = 1048576;
}
