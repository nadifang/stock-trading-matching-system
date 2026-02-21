package com.stms.match.service.impl;

import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.TransactionAsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionAsyncServiceImpl implements TransactionAsyncService {

    @Autowired
    private TransactionMapper transactionMapper;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void saveTransactionAsync(Transaction transaction) {
        transactionMapper.insert(transaction);
    }
}
