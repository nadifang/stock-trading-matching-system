package com.stms.query.service;

import com.stms.query.service.impl.CacheProtectionServiceImpl;

public interface CacheProtectionService {
    void markAsHotOrder(String clOrderId);

    <T> T getWithLock(String securityId, CacheProtectionServiceImpl.OrderDbLoader<T> dbLoader, Class<T> clazz);
}
