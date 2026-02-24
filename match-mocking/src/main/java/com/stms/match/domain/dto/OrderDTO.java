package com.stms.match.domain.dto;

import lombok.Data;

@Data
public class OrderDTO {
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Long qty;
    private Double price;
    private String shareholderId;
}
