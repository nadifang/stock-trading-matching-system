package com.stms.quotation.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.common.Util.Result;
import com.stms.common.constant.QuotationCodeConstant;
import com.stms.common.constant.QuotationReasonConstant;
import com.stms.common.domain.vo.errorMsg;
import com.stms.quotation.domain.vo.quotationVO;
import com.stms.quotation.mapper.quotationMapper;
import com.stms.common.domain.entity.quotation;
import com.stms.quotation.service.quotationService;
import org.springframework.stereotype.Service;

@Service
public class quotationServiceImpl extends ServiceImpl<quotationMapper, quotation> implements quotationService {
    @Override
    public Result getInfo(String securityId) {

        quotation quotationDB = lambdaQuery().eq(quotation::getSecurityId, securityId).one();
        if (quotationDB == null) {
            return Result.fail(new errorMsg(QuotationReasonConstant.QUOTATION_NOT_EXIST_REASON, QuotationCodeConstant.QUOTATION_NOT_EXIST_CODE));
        }

        return Result.ok(quotationVO.builder()
                .market(quotationDB.getMarket())
                .securityId(quotationDB.getSecurityId())
                .askPrice(quotationDB.getAskPrice())
                .bidPrice(quotationDB.getBidPrice())
                .updateTime(quotationDB.getUpdateTime())
                .build());
    }
}
