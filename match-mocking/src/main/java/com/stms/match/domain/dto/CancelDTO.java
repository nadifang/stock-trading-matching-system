package com.stms.match.domain.dto;

import lombok.Data;

@Data
public class CancelDTO {
    private String orderId;
    private String originalOrderId;
    private String market;
    private String securityId;
    private String side;
    private String shareholderId;
}
