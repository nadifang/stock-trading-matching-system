package com.stms.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.trade.domain.dto.orderCanceledDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.utils.CanceledResponse;

public interface orderCanceledService extends IService<Order> {


    CanceledResponse cancelOrder(orderCanceledDTO order);
}
