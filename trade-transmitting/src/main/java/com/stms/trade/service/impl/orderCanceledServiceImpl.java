package com.stms.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.constant.RedisConstant;
import com.stms.trade.domain.dto.orderCanceledDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.OrderEventPublisherService;
import com.stms.trade.service.orderCanceledService;
import com.stms.trade.utils.CanceledResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class orderCanceledServiceImpl extends ServiceImpl<orderMapper, Order> implements orderCanceledService {


    @Resource
    private OrderEventPublisherService orderEventPublisherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public CanceledResponse cancelOrder(orderCanceledDTO order) {

        //1.格式化订单
        CanceledResponse canceledResponse = convertToOrderCommand(order);

        //2.修改订单状态
        //2.1 修改数据库订单状态
        lambdaUpdate().eq(Order::getClOrderId, canceledResponse.getClOrderId())
                .set(Order::getStatus, OrderStatusConstant.ORDER_STATUS_CANCELED)
                .update();

        //2.2 修改redis中订单状态
        String redisKey = RedisConstant.ORDER_STATUS_KEY + canceledResponse.getClOrderId();
        //2.3 获取当前订单信息
        String orderJson = stringRedisTemplate.opsForValue().get(redisKey);

        if (StringUtils.isNotBlank(orderJson)) {
            try {
                // 解析JSON为Map
                Map<String, Object> orderMap = objectMapper.readValue(orderJson, new TypeReference<Map<String, Object>>() {});

                // 更新状态和其他可能变化的字段
                orderMap.put("status", OrderStatusConstant.ORDER_STATUS_CANCELED);

                // 将更新后的Map重新序列化为JSON
                String updatedOrderJson = objectMapper.writeValueAsString(orderMap);

                // 存储更新后的订单信息并重置TTL为一天
                stringRedisTemplate.opsForValue().set(redisKey, updatedOrderJson, 1, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("更新Redis中订单状态失败，订单号：{}", canceledResponse.getClOrderId(), e);
            }
        } else {
            log.warn("Redis中未找到订单信息，订单号：{}", canceledResponse.getClOrderId());
        }

        //3.使用RabbitMQ把订单信息发送到撮合系统，异步处理
//            OrderCanceledEvent event = new OrderCanceledEvent(
//                    canceledResponse.getClOrderId(),
//                    canceledResponse.getOrigClOrderId(),
//                    canceledResponse.getMarket(),
//                    canceledResponse.getSecurityId(),
//                    canceledResponse.getSide(),
//                    canceledResponse.getShareholderId()
//            );
//            orderEventPublisherService.cancelOrderEvent(event);

        //3.获取修改后的订单完整信息
        Order orderInfo = lambdaQuery()
                .eq(Order::getClOrderId, canceledResponse.getClOrderId())
                .one();

        BeanUtil.copyProperties(orderInfo, canceledResponse);

        //4.返回结果
        return canceledResponse;
    }

    public CanceledResponse convertToOrderCommand(orderCanceledDTO order){
        String clOrderId = order.getClOrderId() != null ? new String(order.getClOrderId()) : null;
        String origClOrderId = order.getOrigClOrderId() != null ? new String(order.getOrigClOrderId()) : null;
        String market = order.getMarket() != null ? new String(order.getMarket()) : null;
        String securityId = order.getSecurityId() != null ? new String(order.getSecurityId()) : null;
        String side = order.getSide() != null ? new String(order.getSide()) : null;
        String shareholderId = order.getShareholderId() != null ? new String(order.getShareholderId()) : null;
        return new CanceledResponse(clOrderId,origClOrderId, market, securityId, shareholderId, side);
    }
}
