package com.stms.trade.service.impl;

import com.stms.common.constant.RabbitMQConstant;
import com.stms.trade.service.OrderEventPublisherService;
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
                RabbitMQConstant.EXCHANGE_NAME,
                RabbitMQConstant.ROUTING_KEY,
                event
        );
        log.info("已发送订单创建事件: {}", event.getClOrderId());
    }
}
