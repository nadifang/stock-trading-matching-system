package com.stms.match.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.MatchLogConstant;
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
            log.info(MatchLogConstant.MQ_RECEIVE_ORDER, order.getClOrderId());
            matchService.processOrder(order); // 直接调用队友的方法
            log.info(MatchLogConstant.MQ_PROCESS_ORDER_SUCCESS, order.getClOrderId());
        } catch (Exception e) {
            log.error(MatchLogConstant.MQ_PROCESS_ORDER_FAIL, orderJson, e);
        }
    }

    // 监听撤单队列
    @RabbitListener(queues = RabbitMQConstant.CANCEL_QUEUE_NAME)
    public void handleCancelFromMQ(String cancelJson) {
        try {
            CancelDTO cancelDTO = objectMapper.readValue(cancelJson, CancelDTO.class);
            log.info(MatchLogConstant.MQ_RECEIVE_CANCEL, cancelDTO.getOrigClOrderId());
            boolean cancelResult = matchService.cancelOrder(cancelDTO);
            if (cancelResult) {
                log.info(MatchLogConstant.MQ_CANCEL_SUCCESS, cancelDTO.getOrigClOrderId());
            } else {
                log.warn(MatchLogConstant.MQ_CANCEL_FAIL, cancelDTO.getOrigClOrderId());
            }
        } catch (Exception e) {
            log.error(MatchLogConstant.MQ_PROCESS_CANCEL_FAIL, cancelJson, e);
        }
    }
}