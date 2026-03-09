package com.stms.match.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.common.domain.entity.quotation;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.utils.MatchingOrder;
import com.stms.quotation.domain.dto.quotationDTO;
import com.stms.quotation.domain.vo.quotationVO;

public interface TradeSyncDatabaseService extends IService<quotation> {
    void syncTradeToDatabase(MatchingOrder buyOrder, MatchingOrder sellOrder, Long execQty, Double execPrice, Transaction transaction);

    void syncCancelToDatabase(String orderId, Long canceledQty);

    void hadQuotation(quotationDTO quotationDTO);

    quotation getQuotationBySecurityId(String securityId);
}
