package com.stms.risk.service;

import com.stms.risk.task.OrderCreatedEvent;

public interface RiskReturnService {
    void handleRiskSuccess(OrderCreatedEvent event);
    void handleRiskError(OrderCreatedEvent event, String errorMsg);
}