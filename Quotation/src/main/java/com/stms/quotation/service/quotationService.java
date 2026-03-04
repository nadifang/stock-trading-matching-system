package com.stms.quotation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.common.Util.Result;
import com.stms.common.domain.entity.quotation;

public interface quotationService extends IService<quotation> {
    Result getInfo(String securityId);
}
