package com.stms.query.service.impl;

import com.stms.query.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 限流配置
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";
    // 默认限流配置（每秒最多 N 次）
    private static final int DEFAULT_RATE = 100;  // 每秒100次
    private static final int DEFAULT_BURST = 200; // 突发200次
    // 单用户限流（每分钟最多 N 次）
    private static final int USER_RATE = 30;  // 每用户每分钟30次
    private static final int USER_BURST = 50;  // 每用户突发50次

    /**
     * 通用限流（基于令牌桶算法）
     */
    public boolean tryAcquire(String key, int rate, int burst) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(redisKey);

        // 初始化：速率 rate 次/秒，突发 burst 个令牌
        rateLimiter.trySetRate(RateType.OVERALL, rate, 1, RateIntervalUnit.SECONDS);

        return rateLimiter.tryAcquire(1);
    }

    /**
     * 通用限流（使用默认配置）
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_RATE, DEFAULT_BURST);
    }

    /**
     * 单用户限流（基于用户ID）
     */
    public boolean tryAcquireByUser(String userId) {
        String userKey = "user:" + userId;
        return tryAcquire(userKey, USER_RATE, USER_BURST);
    }

    /**
     * 单IP限流
     */
    public boolean tryAcquireByIp(String ip) {
        String ipKey = "ip:" + ip;
        return tryAcquire(ipKey, USER_RATE, USER_BURST);
    }

    /**
     * 订单查询专用限流（组合限流）
     */
    public boolean tryAcquireOrderQuery(String userId, String ip) {
        // 1. 全局限流
        if (!tryAcquire("order:query:global")) {
            log.warn("全局订单查询限流触发");
            return false;
        }

        // 2. 用户限流
        if (!tryAcquireByUser(userId)) {
            log.warn("用户订单查询限流触发，用户ID：{}", userId);
            return false;
        }

        // 3. IP限流
        if (!tryAcquireByIp(ip)) {
            log.warn("IP订单查询限流触发，IP：{}", ip);
            return false;
        }

        return true;
    }

    /**
     * 滑动窗口限流（更精确）
     */
    public boolean tryAcquireSlidingWindow(String key, int limit, int windowSeconds) {
        String currentWindow = String.valueOf(System.currentTimeMillis() / (windowSeconds * 1000));
        String windowKey = RATE_LIMIT_PREFIX + key + ":" + currentWindow;

        Long count = stringRedisTemplate.opsForValue().increment(windowKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(windowKey, windowSeconds, TimeUnit.SECONDS);
        }

        return count != null && count <= limit;
    }
}
