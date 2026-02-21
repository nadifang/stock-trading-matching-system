package com.stms.risk.config;

import com.stms.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {


    // 声明交换机（Direct 类型）
    @Bean
    public DirectExchange tradeEventsExchange() {
        return new DirectExchange(RabbitMQConstant.EXCHANGE_NAME, true, false);  //持久化且不自动删除
    }

    // 声明队列
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(RabbitMQConstant.QUEUE_NAME).build();   // 持久化队列，服务器重启后仍然存在
    }

    // 绑定队列到交换机
    @Bean
    public Binding bindingOrderCreatedQueue(Queue orderCreatedQueue, DirectExchange tradeEventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(tradeEventsExchange).with(RabbitMQConstant.ROUTING_KEY);   //严格绑定exchange和queue
    }
}
