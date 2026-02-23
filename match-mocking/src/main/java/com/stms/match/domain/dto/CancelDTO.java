package com.stms.match.domain.dto;

import lombok.Data;

@Data
public class CancelDTO {
    private String clOrderId;
    private String origClOrderId;
    private String market;
    private String securityId;
    private String side;
    private String shareholderId;
}
