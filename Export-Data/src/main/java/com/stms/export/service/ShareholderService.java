package com.stms.export.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.export.domian.vo.Top10_shareholderIdVO;
import com.stms.trade.domain.entity.Order;

import java.time.LocalDate;

public interface ShareholderService extends IService<Order> {
    Top10_shareholderIdVO getShareholderStatistics(LocalDate startTime, LocalDate endTime);
}
