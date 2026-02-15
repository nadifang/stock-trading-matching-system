package com.stms.trade_return.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    // 配置WebSocket消息代理
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue", "/topic"); // 客户端订阅的地址
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");     // 启用用户级推送
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")                  // WebSocket 连接端点
                .setAllowedOriginPatterns("*")
                .withSockJS();                       // 支持 SockJS（兼容浏览器）
    }
}
