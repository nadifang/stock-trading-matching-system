package com.stms.match.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TransactionConfirmVO {
    private String orderId;
    private String market;
    private String securityId;
    private String side;
    private Long qty;
    private Double price;
    private String shareholderId;
    private String execId;
    private Long execQty;
    private Double execPrice;
}
