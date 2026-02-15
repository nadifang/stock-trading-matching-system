package com.stms.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.orderService;
import com.stms.trade.utils.TradeRespose;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;

@Service
public class orderServiceImpl extends ServiceImpl<orderMapper, Order> implements orderService{

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public TradeRespose submitOrder(orderDTO order) {

        //1.订单格式化转换
        Order orderCommand = convertToOrderCommand(order);

        //2.订单合法性验证
        TradeRespose tradeRespose =  validateOrder(orderCommand);
        BeanUtil.copyProperties(orderCommand, tradeRespose);

        //3.订单入库
        if(tradeRespose.isSuccess()){

            save(orderCommand);

            //4.使用kafka消息队列把订单数据发送到模拟撮合系统，异步处理
            try {
                ObjectMapper mapper = new ObjectMapper();

                String json = mapper.writeValueAsString(order);

                kafkaTemplate.send("trade.order.in", json);

            } catch (JsonProcessingException e) {

                throw new RuntimeException(e);
            }

        }

        //5.返回订单
        return tradeRespose;
    }

    private TradeRespose validateOrder(Order order) {
        if (order == null) {
            return TradeRespose.fail(1001, "订单为空");
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            return TradeRespose.fail(1002, "订单ID必须为16位");
        }
        if (order.getMarket() == null || !Set.of("XSHG", "XSHE", "BJSE").contains(order.getMarket())) {
            return TradeRespose.fail(1003, "市场代码非法");
        }
        if (order.getSecurityId() == null || order.getSecurityId().length() != 6) {
            return TradeRespose.fail(1004, "股票代码必须为6位");
        }
        if (order.getSide() == null || (!order.getSide().equals("B") && !order.getSide().equals("S"))) {
            return TradeRespose.fail(1005, "买卖方向必须为 B 或 S");
        }
        if (order.getQty() <= 0) {
            return TradeRespose.fail(1006, "数量必须大于0");
        }
        if (order.getPrice() <= 0) {
            return TradeRespose.fail(1007, "价格必须大于0");
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            return TradeRespose.fail(1008, "股东号必须为10位");
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
