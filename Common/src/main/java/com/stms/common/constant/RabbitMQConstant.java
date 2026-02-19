package com.stms.common.constant;

public class RabbitMQConstant {

    // 交换机名称
    public static final String EXCHANGE_NAME = "trade.events.exchange";

    // 队列名称
    public static final String QUEUE_NAME = "order.created.queue";

    // 路由键
    public static final String ROUTING_KEY = "order.created";
}
