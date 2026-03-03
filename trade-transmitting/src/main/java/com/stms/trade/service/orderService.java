package com.stms.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.common.domain.entity.Order;
import com.stms.trade.utils.TradeRespose;

public interface orderService extends IService<Order> {
    TradeRespose submitOrder(orderDTO order);
}
