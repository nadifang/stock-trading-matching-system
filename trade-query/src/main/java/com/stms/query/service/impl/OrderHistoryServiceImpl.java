package com.stms.query.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.common.domain.entity.Order;
import com.stms.query.mapper.queryMapper;
import com.stms.query.service.OrderHistoryService;
import org.springframework.stereotype.Service;

@Service
public class OrderHistoryServiceImpl extends ServiceImpl<queryMapper, Order> implements OrderHistoryService {
    @Override
    public Object getHistory(String securityId) {
        return null;
    }
}
