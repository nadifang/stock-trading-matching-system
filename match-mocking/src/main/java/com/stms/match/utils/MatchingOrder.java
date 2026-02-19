package com.stms.match.utils;

import com.stms.match.domain.dto.OrderDTO;
import lombok.Data;

@Data
public class MatchingOrder {
    private final OrderDTO orderDTO;
    private Long remQty;
    private Long time;

    public MatchingOrder(OrderDTO orderDTO) {
        this.orderDTO = orderDTO;
        this.remQty = orderDTO.getQty();
        this.time = System.nanoTime();
    }
}
