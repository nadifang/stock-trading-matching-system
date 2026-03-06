package com.stms.risk.config;

import com.stms.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class RabbitMQConfig {

    //@Autowired
    private RabbitMQProperties rabbitMQProperties;

    // 风控模块监听的队列
    //@Bean
    public Queue riskOrderCreatedQueue() {
        return QueueBuilder.durable(rabbitMQProperties.getQueue().getOrder()).build();
    }

    // 回报交换机
   // @Bean
    public DirectExchange riskReturnExchange() {
        return new DirectExchange(rabbitMQProperties.getExchange().getReturnExchange());
    }

    // 绑定到交易模块的交换机（直接使用交易模块的交换机名称常量）
    //@Bean
    public Binding bindingRiskOrderCreatedQueue(Queue riskOrderCreatedQueue) {
        return BindingBuilder.bind(riskOrderCreatedQueue)
                .to(new DirectExchange(RabbitMQConstant.TRADE_EXCHANGE))
                .with(RabbitMQConstant.TRANSMIT_ROUTING_KEY);
    }
}