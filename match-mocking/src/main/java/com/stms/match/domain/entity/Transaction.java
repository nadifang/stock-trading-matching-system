package com.stms.match.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("tb_transaction")
public class Transaction {
    // 主键：exec_id，和数据库表的主键对应
    @TableId("exec_id")
    private String execId;

    private String securityId;

    // 用BigDecimal对应decimal(15,4)
    private BigDecimal execPrice;

    private Integer execQty;

    private String buyOrderId;

    private String sellOrderId;

    // 删掉了id、market、buyerId、sellerId，这些在数据库表中不存在
}