package com.stms.query.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.mapper.queryMapper;
import com.stms.query.service.CacheProtectionService;
import com.stms.query.service.HotOrderService;
import com.stms.query.service.OrderQueryService;
import com.stms.common.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class OrderQueryServiceImpl extends ServiceImpl<queryMapper, Order> implements OrderQueryService{

    @Resource
    private CacheProtectionService cacheProtectionService;
    @Resource
    private HotOrderService hotOrderService;

    @Override
    public OrderDetailVO getOrderById(String securityId) {

        // 1. 热点订单识别
        hotOrderService.recordAccess(securityId);

        // 2. 带分布式锁的缓存查询（防击穿）
        return cacheProtectionService.getWithLock(
                securityId,
                // 使用 Lambda 表达式实现 OrderDbLoader
                () -> getOrderFromDB(securityId),
                OrderDetailVO.class
        );
    }

    /**
     * 从数据库查询订单
     */
    private OrderDetailVO getOrderFromDB(String securityId) {
        Order order = lambdaQuery()
                .eq(Order::getSecurityId, securityId)
                .one();
        return order != null ? convertToOrderDetail(order) : null;
    }

    /**
     * 转换 Order 为 OrderDetail
     */
    private OrderDetailVO convertToOrderDetail(Order order) {
        OrderDetailVO detail = new OrderDetailVO();
        BeanUtil.copyProperties(order, detail);
        return detail;
    }
}
