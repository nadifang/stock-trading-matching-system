package com.stms.risk.listener;

import com.stms.risk.service.RiskHandleService;
import com.stms.risk.task.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCreatedEventListener {

    @Autowired
    private RiskHandleService riskHandleService;

    @RabbitListener(queues = "${rabbitmq.queue.order}")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order created event: {}", event);
        try {
            riskHandleService.processOrder(event);
        } catch (Exception e) {
            log.error("Error processing order: {}", event, e);
            // 可做异常处理，如发送到死信队列
        }
    }
}