package com.stms.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.trade.domain.dto.orderCanceledDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.OrderEventPublisherService;
import com.stms.trade.service.orderCanceledService;
import com.stms.trade.utils.CanceledResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class orderCanceledServiceImpl extends ServiceImpl<orderMapper, Order> implements orderCanceledService {


    @Resource
    private OrderEventPublisherService orderEventPublisherService;

    @Override
    public CanceledResponse cancelOrder(orderCanceledDTO order) {

        //1.格式化订单
        CanceledResponse canceledResponse = convertToOrderCommand(order);

        //2.修改订单状态
        lambdaUpdate().eq(Order::getClOrderId, canceledResponse.getClOrderId())
                .set(Order::getStatus, OrderStatusConstant.ORDER_STATUS_CANCELED)
                .update();

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
