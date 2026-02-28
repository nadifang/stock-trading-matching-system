package com.stms.match.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RabbitMQConstant;
import com.stms.trade_return.domain.VO.TradeReturnVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class MatchResultMQProducer {

    // 注入RabbitMQ模板（Spring AMQP核心类，用于发送消息）
    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 注入JSON转换工具（和Listener里的一致）
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 核心发送方法：往撮合回报队列发送TradeReturnVO
     * @param vo 要发送的撮合结果VO
     * @return 是否发送成功
     */
    public boolean sendMatchResultToTradeReturn(TradeReturnVO vo) {
        try {
            // 1. 把VO转成JSON字符串（和接收端解析逻辑一致）
            String resultJson = objectMapper.writeValueAsString(vo);

            // 2. 发送到撮合回报的交换机+路由键（对应配置里的matchReturn通道）
            rabbitTemplate.convertAndSend(
                    RabbitMQConstant.MATCH_RETURN_EXCHANGE_NAME,  // 撮合回报交换机名
                    RabbitMQConstant.MATCH_RETURN_ROUTING_KEY,    // 撮合回报路由键
                    resultJson                                     // 消息体（JSON字符串）
            );

            // 3. 打印日志（和Listener风格统一）
            log.info("MQ发送撮合结果到trade-return成功 | 订单ID:{}",
                    new String(vo.getClOrderId()));
            return true;

        } catch (JsonProcessingException e) {
            // JSON转换失败的异常
            log.error("MQ发送失败：TradeReturnVO转JSON异常 | 订单ID:{}",
                    new String(vo.getClOrderId()), e);
            return false;
        } catch (Exception e) {
            // 其他异常（比如MQ连接失败）
            log.error("MQ发送撮合结果到trade-return异常 | 订单ID:{}",
                    new String(vo.getClOrderId()), e);
            return false;
        }
    }
}