package com.stms.risk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMQProperties {
    private QueueConfig queue = new QueueConfig();
    private ExchangeConfig exchange = new ExchangeConfig();
    private RoutingKeyConfig routingKey = new RoutingKeyConfig();

    @Data
    public static class QueueConfig {
        private String order;
    }

    @Data
    public static class ExchangeConfig {
        private String returnExchange;
    }

    @Data
    public static class RoutingKeyConfig {
        private String returnKey;
    }
}