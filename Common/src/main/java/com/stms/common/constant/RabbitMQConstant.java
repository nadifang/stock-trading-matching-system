package com.stms.common.constant;

public class RabbitMQConstant {


    // ============ 订单创建通道 ============

    // 订单转发交换机名称
    public static final String TRANSMIT_EXCHANGE_NAME = "trade.events.exchange";

    // 订单转发队列名称
    public static final String TRANSMIT_QUEUE_NAME = "order.created.queue";

    // 订单转发路由键
    public static final String TRANSMIT_ROUTING_KEY = "order.created";



    // ============ 撤单通道 ============

    //撤单转发交换机名称
    public static final String CANCEL_EXCHANGE_NAME = "trade.canceled.exchange";

    // 撤单队列
    public static final String CANCEL_QUEUE_NAME = "order.canceled.queue";

    // 撤单路由键
    public static final String CANCEL_ROUTING_KEY = "order.canceled";



    // ============ 风控返回通道 ============

    // 订单转发交换机名称
    public static final String RISK_RETURN_EXCHANGE_NAME = "risk.trade.return.exchange";

    // 订单转发队列名称
    public static final String RISK_RETURN_QUEUE_NAME = "risk.order.return.queue";

    // 订单转发路由键
    public static final String RISK_RETURN_ROUTING_KEY = "risk.order.return";



    // ============ 撮合返回通道 ============

    // 订单转发交换机名称
    public static final String MATCH_RETURN_EXCHANGE_NAME = "match.trade.return.exchange";

    // 订单转发队列名称
    public static final String MATCH_RETURN_QUEUE_NAME = "match.order.return.queue";

    // 订单转发路由键
    public static final String MATCH_RETURN_ROUTING_KEY = "match.order.return";



    // ============ 风控转发通道 ============

    // 订单转发交换机名称
    public static final String RISK_CONTROLLER_EXCHANGE_NAME = "risk.trade.events.exchange";

    // 订单转发队列名称
    public static final String RISK_CONTROLLER_QUEUE_NAME = "risk.order.created.queue";

    // 订单转发路由键
    public static final String RISK_CONTROLLER_ROUTING_KEY = "risk.order.created";

}
