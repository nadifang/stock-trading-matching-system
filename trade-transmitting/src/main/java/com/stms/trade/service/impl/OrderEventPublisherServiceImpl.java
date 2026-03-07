package com.stms.trade.service.impl;

import com.stms.common.constant.RabbitMQConstant;
import com.stms.trade.service.OrderEventPublisherService;
import com.stms.trade.task.OrderCanceledEvent;
import com.stms.trade.task.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class OrderEventPublisherServiceImpl implements OrderEventPublisherService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConstant.TRANSMIT_EXCHANGE_NAME,
                RabbitMQConstant.TRANSMIT_ROUTING_KEY,
                event
        );
        log.info("交易转发已发送订单创建事件: {}", event.getClOrderId());
    }

    public void cancelOrderEvent(OrderCanceledEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConstant.CANCEL_EXCHANGE_NAME,
                RabbitMQConstant.CANCEL_ROUTING_KEY,
                event
        );
        log.info("交易转发已发送订单撤单事件: {}", event.getClOrderId());
    }
}
