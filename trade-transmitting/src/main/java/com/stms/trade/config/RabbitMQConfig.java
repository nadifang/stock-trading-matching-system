package com.stms.trade.config;

import com.stms.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ============ 订单创建通道 ============

    // 声明交换机（Direct 类型）
    @Bean
    public DirectExchange tradeEventsExchange() {
        return new DirectExchange(RabbitMQConstant.TRANSMIT_EXCHANGE_NAME, true, false);  //持久化且不自动删除
    }

    // 声明队列
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(RabbitMQConstant.TRANSMIT_QUEUE_NAME).build();   // 持久化队列，服务器重启后仍然存在
    }

    // 绑定队列到交换机
    @Bean
    public Binding bindingOrderCreatedQueue(Queue orderCreatedQueue, DirectExchange tradeEventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(tradeEventsExchange).with(RabbitMQConstant.TRANSMIT_ROUTING_KEY);   //严格绑定exchange和queue
    }

    // ============ 撤单通道 ============

    @Bean
    public DirectExchange tradeCanceledExchange() {
        return new DirectExchange(RabbitMQConstant.CANCEL_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue orderCanceledQueue() {
        return QueueBuilder.durable(RabbitMQConstant.CANCEL_QUEUE_NAME).build();
    }

    @Bean
    public Binding bindingOrderCanceledQueue(Queue orderCanceledQueue, DirectExchange tradeCanceledExchange) {
        return BindingBuilder.bind(orderCanceledQueue).to(tradeCanceledExchange).with(RabbitMQConstant.CANCEL_ROUTING_KEY);
    }

    // ============ 风控回报通道 ============

    @Bean
    public DirectExchange riskReturnExchange() {
        return new DirectExchange(RabbitMQConstant.RISK_RETURN_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue riskReturnQueue() {
        return QueueBuilder.durable(RabbitMQConstant.RISK_RETURN_QUEUE_NAME).build();
    }

    @Bean
    public Binding bindingRiskReturnQueue(Queue orderCanceledQueue, DirectExchange tradeCanceledExchange) {
        return BindingBuilder.bind(orderCanceledQueue).to(tradeCanceledExchange).with(RabbitMQConstant.RISK_RETURN_ROUTING_KEY);
    }

    // ============ 撮合回报通道 ============

    @Bean
    public DirectExchange matchReturnExchange() {
        return new DirectExchange(RabbitMQConstant.MATCH_RETURN_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue matchReturnQueue() {
        return QueueBuilder.durable(RabbitMQConstant.MATCH_RETURN_QUEUE_NAME).build();
    }

    @Bean
    public Binding bindingMatchReturnQueue(Queue orderCanceledQueue, DirectExchange tradeCanceledExchange) {
        return BindingBuilder.bind(orderCanceledQueue).to(tradeCanceledExchange).with(RabbitMQConstant.MATCH_RETURN_ROUTING_KEY);
    }

    // ============ 风控转发通道 ============

    @Bean
    public DirectExchange riskControllerExchange() {
        return new DirectExchange(RabbitMQConstant.RISK_CONTROLLER_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue riskControllerQueue() {
        return QueueBuilder.durable(RabbitMQConstant.RISK_CONTROLLER_QUEUE_NAME).build();
    }

    @Bean
    public Binding bindingRiskControllerQueue(Queue orderCanceledQueue, DirectExchange tradeCanceledExchange) {
        return BindingBuilder.bind(orderCanceledQueue).to(tradeCanceledExchange).with(RabbitMQConstant.RISK_CONTROLLER_ROUTING_KEY);
    }
}
