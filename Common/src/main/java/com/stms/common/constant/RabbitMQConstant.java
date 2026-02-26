package com.stms.common.constant;

public class RabbitMQConstant {

    // 订单转发交换机名称
    public static final String TRANSMIT_EXCHANGE_NAME = "trade.events.exchange";

    // 订单转发队列名称
    public static final String TRANSMIT_QUEUE_NAME = "order.created.queue";

    // 订单转发路由键
    public static final String TRANSMIT_ROUTING_KEY = "order.created";

    //撤单转发交换机名称
    public static final String CANCEL_EXCHANGE_NAME = "trade.canceled.exchange";

    // 撤单队列
    public static final String CANCEL_QUEUE_NAME = "order.canceled.queue";

    // 撤单路由键
    public static final String CANCEL_ROUTING_KEY = "order.canceled";

    public static final String TRADE_EXCHANGE = "trade.exchange";

}
