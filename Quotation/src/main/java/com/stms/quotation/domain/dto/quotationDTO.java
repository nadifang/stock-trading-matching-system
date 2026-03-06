package com.stms.quotation.domain.dto;

import lombok.Data;

@Data
public class quotationDTO {

    public char[] market;

    public char[] securityId;

    public double bidPrice;

    public double askPrice;
}
