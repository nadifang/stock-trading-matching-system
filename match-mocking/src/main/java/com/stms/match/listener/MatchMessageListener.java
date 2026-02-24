package com.stms.match.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RabbitMQConstant;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchMessageListener {

    @Autowired
    private MatchService matchService; // 注入队友的Service接口

    @Autowired
    private ObjectMapper objectMapper;

    // 监听订单队列
    @RabbitListener(queues = RabbitMQConstant.TRANSMIT_QUEUE_NAME)
    public void handleOrderFromMQ(String orderJson) {
        try {
            OrderDTO order = objectMapper.readValue(orderJson, OrderDTO.class);
            log.info("从MQ收到风控订单：{}", order.getOrderId());
            matchService.processOrder(order); // 直接调用队友的方法
            log.info("MQ订单处理完成：{}", order.getOrderId());
        } catch (Exception e) {
            log.error("处理MQ订单失败：{}", orderJson, e);
        }
    }

    // 监听撤单队列
    @RabbitListener(queues = RabbitMQConstant.CANCEL_QUEUE_NAME)
    public void handleCancelFromMQ(String cancelJson) {
        try {
            CancelDTO cancelDTO = objectMapper.readValue(cancelJson, CancelDTO.class);
            log.info("从MQ收到风控撤单：原订单ID={}", cancelDTO.getOriginalOrderId());
            boolean cancelResult = matchService.cancelOrder(cancelDTO);
            if (cancelResult) {
                log.info("撤单成功：原订单ID={}", cancelDTO.getOriginalOrderId());
            } else {
                log.warn("撤单失败：原订单ID={}（已成交/不存在）", cancelDTO.getOriginalOrderId());
            }
        } catch (Exception e) {
            log.error("处理MQ撤单失败：{}", cancelJson, e);
        }
    }
}