package com.stms.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.common.domain.entity.Order;


public interface RedisSecurityService extends IService<Order> {
    void addOrderIdToBloomFilter(String clOrderId);

    boolean updateOrderStatusWithProtection(String clOrderId, String status);

    boolean isDuplicateOrderRequest(String clOrderId);
}
