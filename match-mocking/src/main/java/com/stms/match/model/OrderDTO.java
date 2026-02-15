package com.stms.match.model;

import lombok.Data;

@Data
public class OrderDTO {
    private String orderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;
}
