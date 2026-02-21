package com.stms.risk.task;

import lombok.Data;

@Data
public class OrderCreatedEvent {

    private String eventId;
    private String eventType;
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Long qty;
    private Double price;
    private String shareholderId;
    private String timestamp;

}
