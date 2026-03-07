package com.stms.common.constant;

public class RedisConstant {

    public static final String ORDER_STATUS_KEY = "security:status:";

    // 空值缓存标记
    public static final String EMPTY_VALUE = "_EMPTY_";

    // 空值缓存 TTL（5分钟）
    public static final long EMPTY_CACHE_TTL = 300;

    // 热点订单阈值（1分钟内访问超过此次数视为热点）
    public static final int HOT_ORDER_THRESHOLD = 10;

    // 热点订单 TTL（2小时）
    public static final long HOT_ORDER_TTL = 7200;

    // 布隆过滤器 key（简单实现，生产可用 RedisBloom 模块或 Guava）
    public static final String BLOOM_FILTER_KEY = "security:bloom:filter";

    // 空值缓存标记
    public static final String EMPTY_VALUE_STRING = "";

    // 分布式锁过期时间（10秒）
    public static final long LOCK_TTL = 10;

    // 随机TTL范围（0-10分钟），防止缓存雪崩
    public static final int RANDOM_TTL_MINUTES = 10;

    // 布隆过滤器 + 滑动窗口的 key 前缀
    public static final String DUPLICATE_ORDER_BF_KEY = "security:bf:duplicate:";

    // 时间窗口（秒），例如 5 秒内不能重复提交
    public static final int DUPLICATE_WINDOW_SECONDS = 10;

    // 布隆过滤器位图长度（2^20 = 1048576 位，够用且省内存）
    public static final int BF_SIZE = 1048576;

    // 用户登录令牌key
    public static final String USER_LOGIN_TOKEN_KEY = "login:token:";

    // 股票查询缓存key
    public static final String ORDER_QUERY_KEY = "security:query:";

    // 分布式锁key
    public static final String DISTRIBUTED_LOCK_KEY = "lock:securityId:query:";

    // 布隆过滤器key
    public static final String BLOOM_KEY = "bloom:order:ids";

    // 热点订单访问计数 key 前缀
    public static final String HOT_SECURITY_COUNT_PREFIX = "hot:security:count:";

    // 热点订单标记 key 前缀
    public static final String HOT_SECURITY_MARK_PREFIX = "hot:security:mark:";

    // 统计窗口（1分钟）
    public static final int WINDOW_SECONDS = 60;

    // 热点阈值
    public static final int HOT_THRESHOLD = 10;

    // 热点 TTL（2小时）
    public static final long HOT_TTL = 7200;

    // 盘口卖盘 ZSet key
    public static final String ORDER_BOOK_ASK_KEY = "orderbook:ask:";

    // 盘口买盘 ZSet key
    public static final String ORDER_BOOK_BID_KEY = "orderbook:bid:";
}
