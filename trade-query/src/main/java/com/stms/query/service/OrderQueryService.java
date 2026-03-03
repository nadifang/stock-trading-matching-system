package com.stms.query.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.common.domain.entity.Order;

import java.util.List;

public interface OrderQueryService extends IService<Order> {
    OrderDetailVO getOrderById(String clOrderId);
}
