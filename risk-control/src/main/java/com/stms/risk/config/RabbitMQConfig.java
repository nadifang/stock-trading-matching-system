package com.stms.risk.config;

import com.stms.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 风控模块专属队列（名称自定义）
    @Bean
    public Queue riskOrderCreatedQueue() {
        return QueueBuilder.durable("risk.order.created.queue").build();
    }

    // 绑定到交易模块的交换机，使用交易模块的路由键
    @Bean
    public Binding bindingRiskOrderCreatedQueue(Queue riskOrderCreatedQueue,
                                                DirectExchange tradeEventsExchange) {
        return BindingBuilder.bind(riskOrderCreatedQueue)
                .to(tradeEventsExchange)
                .with(RabbitMQConstant.TRANSMIT_ROUTING_KEY);
    }
}