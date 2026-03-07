package com.stms.risk.service.impl;

import com.stms.common.constant.RabbitMQConstant;
import com.stms.risk.service.RiskReturnService;
import com.stms.trade.task.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class RiskReturnServiceImpl implements RiskReturnService {

    // 如果下一模块使用 RabbitMQ，可注入 RabbitTemplate 发送消息
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void handleRiskSuccess(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConstant.RISK_CONTROLLER_EXCHANGE_NAME,
                RabbitMQConstant.RISK_CONTROLLER_ROUTING_KEY,
                event
        );
        log.info("风控已发送订单创建事件: {}", event.getClOrderId());
    }

    @Override
    public void handleRiskError(OrderCreatedEvent event, String errorMsg) {
        log.error("Risk check failed for order: {}, error: {}", event.getClOrderId(), errorMsg);
        // 可在此处将错误记录到数据库或发送到死信队列
    }
}