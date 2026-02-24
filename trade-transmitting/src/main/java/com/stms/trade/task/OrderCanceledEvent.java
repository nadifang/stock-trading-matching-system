package com.stms.trade.task;

import com.stms.common.constant.DateConstant;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class OrderCanceledEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clOrderId;         // 要撤的订单ID
    private String origClOrderId;     // 原始订单ID（如果不同）
    private String market;
    private String securityId;
    private String shareholderId;
    private String side;

    /**
     * 唯一标识（带时间戳+随机数）
     */
    private String eventId;

    /**
     * 事件类型
     */
    private String eventType = "ORDER_CANCEL";

    /**
     * 时间戳
     */
    private String timestamp;

    // 构造方法，自动生成 eventId 和时间戳
    public OrderCanceledEvent(String clOrderId, String origClOrderId, String market, String securityId,
                             String side, String shareholderId) {
        this.eventId = "evt_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        this.clOrderId = clOrderId;
        this.origClOrderId = origClOrderId;
        this.market = market;
        this.securityId = securityId;
        this.side = side;
        this.shareholderId = shareholderId;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateConstant.DEFAULT_DATE_TIME_FORMAT));
    }
}
