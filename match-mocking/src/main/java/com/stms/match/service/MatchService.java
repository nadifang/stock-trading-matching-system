package com.stms.match.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Transaction;

public interface MatchService extends IService<Transaction> {

    void processOrder(OrderDTO order);

    boolean cancelOrder(CancelDTO cancelDTO);
}