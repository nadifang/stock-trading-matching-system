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
import com.stms.trade.service.RedisSecurityService;
import com.stms.trade.service.orderCanceledService;
import com.stms.trade.utils.CanceledResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class orderCanceledServiceImpl extends ServiceImpl<orderMapper, Order> implements orderCanceledService {

    @Resource
    private RedisSecurityService redisSecurityService;
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
        boolean order_pending = lambdaQuery()
                .eq(Order::getClOrderId, canceledResponse.getOrigClOrderId())
                .eq(Order::getStatus, OrderStatusConstant.ORDER_STATUS_PENDING)
                .count() > 0;
        if (order_pending) {
            lambdaUpdate().eq(Order::getClOrderId, canceledResponse.getOrigClOrderId())
                    .set(Order::getStatus, OrderStatusConstant.ORDER_STATUS_CANCELED)
                    .update();
        }

        //2.2 修改redis中订单状态
        boolean cacheUpdated = redisSecurityService.updateOrderStatusWithProtection(
                canceledResponse.getOrigClOrderId(),
                OrderStatusConstant.ORDER_STATUS_CANCELED,
                () -> true // 数据库已更新，这里直接返回true
        );

        //2.3 判断写redis是否成功
        if (!cacheUpdated) {
            log.warn("Redis缓存更新失败，订单ID：{}", canceledResponse.getOrigClOrderId());
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

        //4.获取修改后的订单完整信息
        String redisKey = RedisConstant.ORDER_STATUS_KEY + canceledResponse.getOrigClOrderId();
        String orderJson = stringRedisTemplate.opsForValue().get(redisKey);

        Order orderInfo = null;

        if (StringUtils.isNotBlank(orderJson)) {
            try {
                //4.1 解析JSON为Map
                Map<String, Object> orderMap = objectMapper.readValue(orderJson, new TypeReference<Map<String, Object>>() {});
                //4.2 转换为Order对象
                orderInfo = convertMapToOrder(orderMap);
            } catch (Exception e) {
                log.error("Redis订单信息解析失败，订单ID：{}", canceledResponse.getOrigClOrderId(), e);
                // 解析失败，从数据库兜底
                orderInfo = getOrderFromDB(canceledResponse.getOrigClOrderId());
            }
        } else {
            // Redis中没有，从数据库获取
            log.warn("Redis中未找到订单信息，从数据库获取，订单ID：{}", canceledResponse.getOrigClOrderId());
            orderInfo = getOrderFromDB(canceledResponse.getOrigClOrderId());
        }

        // 4.1 如果订单信息为空，抛出异常或处理
        if (orderInfo == null) {
            log.error("撤单后查询订单信息失败，订单ID：{}", canceledResponse.getOrigClOrderId());
            throw new RuntimeException("订单信息查询失败");
        }

        BeanUtil.copyProperties(orderInfo, canceledResponse,"clOrderId");

        //5.返回结果
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

    private Order convertMapToOrder(Map<String, Object> orderMap) {
        Order order = new Order();
        order.setClOrderId((String) orderMap.get("clOrderId"));
        order.setMarket((String) orderMap.get("market"));
        order.setSecurityId((String) orderMap.get("securityId"));
        order.setSide((String) orderMap.get("side"));
        order.setShareholderId((String) orderMap.get("shareholderId"));
        order.setStatus((String) orderMap.get("status"));

        // 处理qty
        order.setQty(parseLong(orderMap.get("qty")));

        // 处理price
        order.setPrice(parseDouble(orderMap.get("price")));

        // 处理cum_qty
        order.setCumQty(parseLong(orderMap.get("cum_qty")));

        // 处理canceled_qty
        order.setCanceledQty(parseLong(orderMap.get("canceled_qty")));

        // 处理remaining_qty
        order.setRemainingQty(parseLong(orderMap.get("remaining_qty")));

        // 处理traded_amount
        order.setTradedAmount(parseLong(orderMap.get("traded_amount")));

        return order;
    }

    private Order getOrderFromDB(String clOrderId) {
        return lambdaQuery()
                .eq(Order::getClOrderId, clOrderId)
                .one();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为 Long: {}", value);
                return 0L;
            }
        }
        log.warn("无法解析类型为{}的值:{}", value.getClass().getName(), value);
        return 0L;
    }

    /**
     * 安全地将Object转换为Double
     */
    private Double parseDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Double:{}", value);
                return 0.0;
            }
        }
        log.warn("无法解析类型为{}的值:{}", value.getClass().getName(), value);
        return 0.0;
    }
}
