package com.stms.risk.service;

import com.stms.trade.task.OrderCreatedEvent;

public interface RiskHandleService {
    void processOrder(OrderCreatedEvent event);
}