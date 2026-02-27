package com.stms.dotest;

import com.stms.common.constant.RabbitMQConstant;
import com.stms.trade.task.OrderCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class dotests {
    @RabbitListener(queues = RabbitMQConstant.TRANSMIT_QUEUE_NAME)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        System.out.println("执行到这里");
        System.out.println("风控收到订单创建事件: " + event.getClOrderId());

    }
}