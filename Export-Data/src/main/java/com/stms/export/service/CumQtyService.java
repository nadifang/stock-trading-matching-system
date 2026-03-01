package com.stms.export.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.export.domian.vo.Top10_cumQtyVO;
import com.stms.trade.domain.entity.Order;

import java.time.LocalDate;

public interface CumQtyService extends IService<Order> {
    Top10_cumQtyVO getCumQtyStatistics(LocalDate startTime, LocalDate endTime);
}
