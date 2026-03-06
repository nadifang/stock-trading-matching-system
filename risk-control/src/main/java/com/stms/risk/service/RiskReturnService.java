package com.stms.risk.service;

import com.stms.trade.task.OrderCreatedEvent;

public interface RiskReturnService {
    void handleRiskSuccess(OrderCreatedEvent event);
    void handleRiskError(OrderCreatedEvent event, String errorMsg);
}