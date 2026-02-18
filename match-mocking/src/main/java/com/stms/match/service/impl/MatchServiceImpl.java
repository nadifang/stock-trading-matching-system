package com.stms.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import org.springframework.stereotype.Service;

@Service
public class MatchServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements MatchService {
}
