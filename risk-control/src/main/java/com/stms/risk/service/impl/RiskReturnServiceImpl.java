package com.stms.risk.service.impl;

import com.stms.risk.service.RiskReturnService;
import com.stms.risk.task.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskReturnServiceImpl implements RiskReturnService {

    // 如果下一模块使用 RabbitMQ，可注入 RabbitTemplate 发送消息
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    private static final String NEXT_EXCHANGE = "trade.return.exchange";
    private static final String NEXT_ROUTING_KEY = "trade.return.key";

    @Override
    public void handleRiskSuccess(OrderCreatedEvent event) {
        log.info("Risk check passed for order: {}", event.getClOrderId());
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(NEXT_EXCHANGE, NEXT_ROUTING_KEY, event);
        } else {
            // 无MQ时仅记录日志
            log.info("Simulating passing order to next module: {}", event);
        }
    }

    @Override
    public void handleRiskError(OrderCreatedEvent event, String errorMsg) {
        log.error("Risk check failed for order: {}, error: {}", event.getClOrderId(), errorMsg);
        // 可在此处将错误记录到数据库或发送到死信队列
    }
}