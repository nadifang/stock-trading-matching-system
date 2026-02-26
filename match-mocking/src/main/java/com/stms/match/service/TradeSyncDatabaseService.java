package com.stms.match.service;

import com.stms.match.domain.entity.Transaction;
import com.stms.match.utils.MatchingOrder;

public interface TradeSyncDatabaseService {
    void syncTradeToDatabase(MatchingOrder buyOrder, MatchingOrder sellOrder, Long execQty, Double execPrice, Transaction transaction);

    void syncCancelToDatabase(String orderId, Long canceledQty);
}
