package com.stms.trade_return.Listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.trade_return.Utils.TradeReturnCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ReturnMessageListener {

    //WebSocket 推送工具
    @Resource
    private SimpMessagingTemplate messagingTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "trade.return.out", groupId = "return-group")
    public void listenReturnMessage(String message) {
        try {
            log.info("收到回报消息: {}", message);

            TradeReturnCommand returnVO = objectMapper.readValue(message, TradeReturnCommand.class);

            String userId = returnVO.getShareholderId();

            if (returnVO.getExecId().isEmpty()) {
                messagingTemplate.convertAndSendToUser(
                        userId,                                   // 目标用户ID，如 "user123"
                        "/queue/returns",                         // 前端订阅的地址
                        returnVO.fail()                           // 直接推送对象，Spring 会自动转 JSON
                );
            }
            else {
                messagingTemplate.convertAndSendToUser(
                        userId,                                   // 目标用户ID，如 "user123"
                        "/queue/returns",                         // 前端订阅的地址
                        returnVO.success()                        // 直接推送对象，Spring 会自动转 JSON
                );
            }

            log.info("已推送回报给用户 [{}]，订单：{}", userId, returnVO.getClOrderId());

        } catch (Exception e) {
            log.error("处理回报消息失败: {}", message, e);
        }
    }

}
