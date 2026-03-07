package com.stms.common.constant;

/**
 * 撮合模块统一日志常量
 */
public class MatchLogConstant {

    // ==================== MQ 监听器日志 ====================
    public static final String MQ_RECEIVE_ORDER = "从MQ收到风控订单：{}";
    public static final String MQ_PROCESS_ORDER_SUCCESS = "MQ订单处理完成：{}";
    public static final String MQ_PROCESS_ORDER_FAIL = "处理MQ订单失败：{}";
    public static final String MQ_RECEIVE_CANCEL = "从MQ收到风控撤单：原订单ID={}";
    public static final String MQ_CANCEL_SUCCESS = "撤单成功：原订单ID={}";
    public static final String MQ_CANCEL_FAIL = "撤单失败：原订单ID={}（已成交/不存在）";
    public static final String MQ_PROCESS_CANCEL_FAIL = "处理MQ撤单失败：{}";

    // ==================== MQ 生产者日志 ====================
    public static final String MQ_SEND_MATCH_SUCCESS = "MQ发送撮合结果到trade-return成功 | 订单ID:{}";
    public static final String MQ_SEND_MATCH_JSON_ERROR = "MQ发送失败：TradeReturnVO转JSON异常 | 订单ID:{}";
    public static final String MQ_SEND_MATCH_EXCEPTION = "MQ发送撮合结果到trade-return异常 | 订单ID:{}";

    // ==================== 撮合核心引擎日志 ====================
    public static final String MATCH_INTERCEPT_STATUS = "拦截：订单状态为 {}，放弃进入撮合池 | 订单ID: {}";
    public static final String MATCH_INTERCEPT_REDIS_ERROR = "拦截：解析Redis订单状态异常，放弃进入撮合池 | 订单ID: {}，异常：{}";
    public static final String MATCH_SYNC_CANCEL_ERROR = "撤单状态同步异常 | 订单ID: {}, 异常: {}";
    public static final String MATCH_EXECUTE_SUCCESS = "撮合成功并同步落库 | 股票: {} | 成交价: {} | 成交量: {}";
    public static final String MATCH_EXECUTE_ROLLBACK = "数据库同步失败，交易回滚 | 股票: {} | 成交价: {} | 成交量: {}";
    public static final String MATCH_REDIS_UPDATE_ERROR = "更新Redis订单状态失败，订单号：{}，异常：{}";
    public static final String MATCH_REDIS_CANCEL_ERROR = "更新Redis订单撤单状态失败，订单号：{}，异常：{}";
}