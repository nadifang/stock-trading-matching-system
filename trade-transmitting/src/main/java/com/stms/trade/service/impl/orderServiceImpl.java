package com.stms.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.OrderIllegalCodeConstant;
import com.stms.common.constant.OrderIllegalReasonConstant;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.OrderEventPublisherService;
import com.stms.trade.service.orderService;
import com.stms.trade.task.OrderCreatedEvent;
import com.stms.trade.utils.TradeRespose;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;

@Service
public class orderServiceImpl extends ServiceImpl<orderMapper, Order> implements orderService{

    @Resource
    private OrderEventPublisherService orderEventPublisherService;

    @Override
    public TradeRespose submitOrder(orderDTO order) {

        //1.订单格式化转换
        Order orderCommand = convertToOrderCommand(order);

        //2.订单合法性验证
        TradeRespose tradeRespose =  validateOrder(orderCommand);
        BeanUtil.copyProperties(orderCommand, tradeRespose);

        //3.订单入库
        if(tradeRespose.isSuccess()){

            //3.1 设置订单状态信息为待处理
            orderCommand.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);

            //3.2 入库
            save(orderCommand);

            //4.使用RabbitMQ把订单数据发送到模拟风控系统，异步处理
//            OrderCreatedEvent event = new OrderCreatedEvent(
//                    orderCommand.getClOrderId(),
//                    orderCommand.getMarket(),
//                    orderCommand.getSecurityId(),
//                    orderCommand.getSide(),
//                    orderCommand.getQty(),
//                    orderCommand.getPrice(),
//                    orderCommand.getShareholderId()
//            );
//            orderEventPublisherService.publishOrderCreatedEvent(event);
        }

        //5.返回订单
        return tradeRespose;
    }

    private TradeRespose validateOrder(Order order) {
        if (order == null) {
            return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_IS_NULL, OrderIllegalReasonConstant.ORDER_IS_NULL_REASON);
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_CL_ORDER_ID_ILLEGAL, OrderIllegalReasonConstant.ORDER_CL_ORDER_ID_ILLEGAL_REASON);
        }
        if (order.getMarket() == null || !Set.of("XSHG", "XSHE", "BJSE").contains(order.getMarket())) {
            return TradeRespose.fail(OrderIllegalCodeConstant.MARKET_CODE_ILLEGAL, OrderIllegalReasonConstant.MARKET_CODE_ILLEGAL_REASON);
        }
        if (order.getSecurityId() == null || order.getSecurityId().length() != 6) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SECURITY_ID_ILLEGAL, OrderIllegalReasonConstant.SECURITY_ID_ILLEGAL_REASON);
        }
        if (order.getSide() == null || (!order.getSide().equals("B") && !order.getSide().equals("S"))) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SIDE_ILLEGAL, OrderIllegalReasonConstant.SIDE_ILLEGAL_REASON);
        }
        if (order.getQty() <= 0) {
            return TradeRespose.fail(OrderIllegalCodeConstant.QTY_ILLEGAL, OrderIllegalReasonConstant.QTY_ILLEGAL_REASON);
        }
        if (order.getPrice() <= 0) {
            return TradeRespose.fail(OrderIllegalCodeConstant.PRICE_ILLEGAL, OrderIllegalReasonConstant.PRICE_ILLEGAL_REASON);
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SHAREHOLDER_ID_ILLEGAL, OrderIllegalReasonConstant.SHAREHOLDER_ID_ILLEGAL_REASON);
        }
        return TradeRespose.success();
    }

    private Order convertToOrderCommand(orderDTO order) {

        String clOrderId = order.getClOrderId() != null ? new String(order.getClOrderId()) : null;
        String market = order.getMarket() != null ? new String(order.getMarket()) : null;
        String securityId = order.getSecurityId() != null ? new String(order.getSecurityId()) : null;
        String side = order.getSide() != null ? new String(order.getSide()) : null;
        String shareholderId = order.getShareholderId() != null ? new String(order.getShareholderId()) : null;
        return new Order(clOrderId, market, securityId, side, order.getQty(), order.getPrice(), shareholderId);
    }
}
