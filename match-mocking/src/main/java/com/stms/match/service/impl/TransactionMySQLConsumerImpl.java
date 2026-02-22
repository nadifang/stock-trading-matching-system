package com.stms.match.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.TransactionMySQLConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TransactionMySQLConsumerImpl implements TransactionMySQLConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // 单线程池用于平滑削峰
    private static final ExecutorService CONSUMER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        CONSUMER_EXECUTOR.submit(new QueueHandler());
    }

    private class QueueHandler implements Runnable {
        @Override
        @SuppressWarnings("BusyWait")
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 使用 BRPOP/rightPop 阻塞弹栈：超时时间为0表示无限期阻塞等待
                    // 由于Lua使用的是LPUSH推入左侧，此处必须从右侧(rightPop)弹出以保证时间先后顺序
                    String json = stringRedisTemplate.opsForList().rightPop(
                            RedisConstant.TRANSACTION_QUEUE_KEY,
                            Duration.ofSeconds(0)
                    );

                    if (json != null) {
                        // 1. 反序列化
                        Transaction transaction = objectMapper.readValue(json, Transaction.class);
                        // 2. MySQL持久化落库
                        transactionMapper.insert(transaction);
                        System.out.println("异步落库成功 | 执行ID: " + transaction.getExecId());
                    }
                } catch (Exception e) {
                    System.err.println("消费Redis队列落库MySQL时发生异常: " + e.getMessage());
                    // 发生异常时休眠1秒，防止死循环疯狂刷错误日志造成CPU飙升
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

}
