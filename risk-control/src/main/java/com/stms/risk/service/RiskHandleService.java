package com.stms.risk.service;

import com.stms.risk.task.OrderCreatedEvent;

public interface RiskHandleService {
    void processOrder(OrderCreatedEvent event);
}