package com.stms.trade.service;

import com.stms.trade.task.OrderCreatedEvent;

public interface OrderEventPublisherService {
    void publishOrderCreatedEvent(OrderCreatedEvent event);
}
