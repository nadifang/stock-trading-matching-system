package com.stms.trade.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReturnCallBack implements RabbitTemplate.ReturnCallback{

    @Override
    public void returnedMessage(org.springframework.amqp.core.Message message, int replyCode, String replyText,
            String exchange, String routingKey) {
        log.info("路由器到队列出问题，路由键");
    }

}
