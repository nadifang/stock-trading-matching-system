package com.stms.trade.service;

import com.stms.trade.service.impl.RedisSecurityServiceImpl;

public interface RedisSecurityService {
    void addOrderIdToBloomFilter(String clOrderId);

    boolean updateOrderStatusWithProtection(String clOrderId, String status, RedisSecurityServiceImpl.OrderDbUpdater dbUpdater);

    boolean isDuplicateOrderRequest(String clOrderId);
}
