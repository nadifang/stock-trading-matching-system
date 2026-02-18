package com.stms.match.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@TableName("tb_transaction")
public class Transaction {

    @TableId("value = id, type = IdType.AUTO")
    private Long id;

    private String execId;
    private String market;
    private String securityId;
    private Integer execQty;
    private Double execPrice;
    private String buyOrderId;
    private String sellOrderId;
    private String buyerId;
    private String sellerId;
}
