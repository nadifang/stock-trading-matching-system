package com.stms.trade.task;

import com.stms.common.constant.DateConstant;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class OrderCreatedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Long qty;
    private Double price;
    private String shareholderId;

    /**
     * 唯一标识（带时间戳+随机数）
     */
    private String eventId;

    /**
     * 事件类型
     */
    private String eventType = "ORDER_CREATED";

    /**
     * 时间戳
     */
    private String timestamp;



    // 构造方法，自动生成 eventId 和时间戳
    public OrderCreatedEvent(String clOrderId, String market, String securityId,
                             String side, Long qty, Double price, String shareholderId) {
        this.eventId = "evt_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        this.clOrderId = clOrderId;
        this.market = market;
        this.securityId = securityId;
        this.side = side;
        this.qty = qty;
        this.price = price;
        this.shareholderId = shareholderId;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateConstant.DEFAULT_DATE_TIME_FORMAT));
    }
}
