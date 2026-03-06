package com.stms.query.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.common.domain.entity.Order;

public interface OrderHistoryService extends IService<Order> {
    Object getHistory(String securityId);
}
