package com.stms.match.domain.entity; // 确认包名正确

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_transaction")
public class Transaction {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String execId;
    private String market;
    private String securityId;
    private Long execQty;
    private Double execPrice;

    // 👇 2. 你原本的set方法保留（或者直接用@Data自动生成，下面为了严谨保留）
    // 👇 1. 新增数据库对应的字段
    private LocalDateTime execTime;

    private String buyOrderId;
    private String sellOrderId;
    private String buyerId;
    private String sellerId;

}