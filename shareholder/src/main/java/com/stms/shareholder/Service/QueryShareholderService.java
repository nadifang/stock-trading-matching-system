package com.stms.shareholder.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.shareholder.domain.dto.Result;
import com.stms.common.domain.entity.Order;


public interface QueryShareholderService extends IService<Order> {
    Result getInfo(String token);
}
