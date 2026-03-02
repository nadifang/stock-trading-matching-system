package com.stms.export.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.export.domian.vo.Top10_securityIdVO;
import com.stms.trade.domain.entity.Order;

import java.time.LocalDate;

public interface SecurityTradeService extends IService<Order> {
    Top10_securityIdVO getSecurityStatistics(LocalDate startTime, LocalDate endTime);
}
