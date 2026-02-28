package com.stms.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.trade.domain.entity.Cancel_Order;

public interface CancelService extends IService<Cancel_Order> {
    boolean cancelOrder(Cancel_Order cancelOrder);
}
