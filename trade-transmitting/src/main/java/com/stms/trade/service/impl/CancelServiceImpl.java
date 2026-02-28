package com.stms.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.trade.domain.entity.Cancel_Order;
import com.stms.trade.mapper.cancelMapper;
import com.stms.trade.service.CancelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelServiceImpl extends ServiceImpl<cancelMapper, Cancel_Order> implements CancelService {

    @Override
    @Transactional
    public boolean cancelOrder(Cancel_Order cancelOrder) {
        return save(cancelOrder);
    }
}
