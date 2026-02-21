package com.stms.match.service;

import com.stms.match.domain.entity.Transaction;

public interface TransactionAsyncService {
    void saveTransactionAsync(Transaction transaction);
}
