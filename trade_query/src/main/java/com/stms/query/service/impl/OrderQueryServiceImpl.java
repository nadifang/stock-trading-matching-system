package com.stms.query.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.mapper.queryMapper;
import com.stms.query.service.CacheProtectionService;
import com.stms.query.service.HotOrderService;
import com.stms.query.service.OrderQueryService;
import com.stms.query.service.RateLimitService;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class OrderQueryServiceImpl extends ServiceImpl<queryMapper, Order> implements OrderQueryService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private CacheProtectionService cacheProtectionService;
    @Resource
    private HotOrderService hotOrderService;
    @Resource
    private RateLimitService rateLimitService;

    @Override
    public OrderDetailVO getOrderById(String clOrderId) {

        // 1. 热点订单识别
        hotOrderService.recordAccess(clOrderId);

        // 2. 带分布式锁的缓存查询（防击穿）
        return cacheProtectionService.getWithLock(
                clOrderId,
                // 使用 Lambda 表达式实现 OrderDbLoader
                () -> getOrderFromDB(clOrderId),
                OrderDetailVO.class
        );
    }

    /**
     * 从数据库查询订单
     */
    private OrderDetailVO getOrderFromDB(String clOrderId) {
        Order order = lambdaQuery()
                .eq(Order::getClOrderId, clOrderId)
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
