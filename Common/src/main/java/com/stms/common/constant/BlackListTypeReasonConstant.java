package com.stms.common.constant;

public class BlackListTypeReasonConstant {

    // ========== 股东相关风险 ==========
    public static final String RISK_REASON_STOCK_MANIPULATION = "涉嫌操纵市场";
    public static final String RISK_REASON_PAIR_TRADING = "涉嫌对敲交易";
    public static final String RISK_REASON_HIGH_FREQUENCY_TRADING = "短时间内高频交易，疑似机器人操作";
    public static final String RISK_REASON_REPEAT_SUBMIT = "短时间内重复提交订单";

    // ========== IP 相关风险 ==========
    public static final String RISK_REASON_HIGH_RISK_IP = "来自高风险地区IP，多次触发风控规则";
    public static final String RISK_REASON_SUSPICIOUS_IP_ACTIVITY = "IP行为异常，多次异常访问";

    // ========== 设备相关风险 ==========
    public static final String RISK_REASON_DEVICE_FINGERPRINT_ABNORMAL = "设备指纹异常，疑似多账号共用设备";
    public static final String RISK_REASON_DEVICE_MULTI_ACCOUNT = "同一设备登录多个账号，行为异常";

    // ========== 账户/用户相关风险 ==========
    public static final String RISK_REASON_ACCOUNT_VIOLATION_HISTORY = "历史违规交易，账户被永久封禁";
    public static final String RISK_REASON_ACCOUNT_UNUSUAL_LOGIN = "账户异常登录，疑似被盗或违规操作";
    public static final String RISK_REASON_ACCOUNT_BEHAVIOR_SUSPICIOUS = "账户行为异常，触发风控模型";

    // ========== 行为相关风险（通用） ==========
    public static final String RISK_REASON_UNUSUAL_TRADING_PATTERN = "交易模式异常，疑似程序化操纵";
    public static final String RISK_REASON_LARGE_ORDER_SPURIOUS = "大额订单疑似虚假申报";
    public static final String RISK_REASON_MARKET_MANIPULATION_SUSPECTED = "涉嫌市场操纵行为";
    public static final String RISK_REASON_INTERNAL_POLICY_VIOLATION = "违反内部风控策略";

    // ========== 其他 / 未知风险 ==========
    public static final String RISK_REASON_OTHER = "其他风控原因";
    public static final String RISK_REASON_SYSTEM_AUTO_BLOCK = "系统自动拦截，原因待核查";

}
