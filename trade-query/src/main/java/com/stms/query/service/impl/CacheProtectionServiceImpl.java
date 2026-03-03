package com.stms.query.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.query.service.CacheProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
    public class CacheProtectionServiceImpl implements CacheProtectionService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ObjectMapper objectMapper;


    /**
     * 布隆过滤器防穿透、空值缓存防穿透、分布式锁防击穿、热点识别、随机TTL防雪崩
     * 带分布式锁的缓存查询（防击穿）
     */
    public <T> T getWithLock(String securityId, OrderDbLoader<T> dbLoader, Class<T> clazz) {
        String redisKey = RedisConstant.ORDER_QUERY_KEY+ securityId;

        // 1. 先查缓存
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isNotBlank(cachedValue) && !RedisConstant.EMPTY_VALUE.equals(cachedValue)) {
            try {
                return objectMapper.readValue(cachedValue, clazz);
            } catch (Exception e) {
                log.error("缓存解析失败，股票ID：{}", securityId, e);
            }
        }

        //TODO 2. 缓存未命中，尝试获取分布式锁
        String lockKey = RedisConstant.DISTRIBUTED_LOCK_KEY + securityId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 最多等待 100ms，锁持有时间 10s
            boolean acquired = lock.tryLock(100, 10000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                // 获取锁失败，返回旧数据或空
                log.warn("获取分布式锁失败，股票ID：{}", securityId);
                return getFromCacheOrNull(redisKey, clazz);
            }

            // 3. 双重检查缓存
            cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
            if (StringUtils.isNotBlank(cachedValue) && !RedisConstant.EMPTY_VALUE.equals(cachedValue)) {
                return objectMapper.readValue(cachedValue, clazz);
            }

            // 4. 查询数据库
            T data = dbLoader.load();

            // 5. 写入缓存
            if (data != null) {
                writeToCacheWithRandomTtl(redisKey, data);
            } else {
                setEmptyCache(redisKey);
            }

            return data;

        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，股票ID：{}", securityId, e);
            Thread.currentThread().interrupt();
            return null;
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 设置热点订单（延长TTL）
     */
    public void markAsHotOrder(String securityId) {
        String redisKey = RedisConstant.ORDER_QUERY_KEY + securityId;
        Boolean exists = stringRedisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.expire(redisKey, RedisConstant.HOT_ORDER_TTL, TimeUnit.SECONDS);
            log.info("股票标记为热点，股票ID：{}，TTL：{}秒", securityId, RedisConstant.HOT_ORDER_TTL);
        }
    }

    /**
     * 布隆过滤器检查
     */
    private boolean checkBloomFilter(String clOrderId) {
        return stringRedisTemplate.opsForValue().getBit(RedisConstant.BLOOM_KEY, hash(clOrderId)) != null;
    }

    /**
     * 设置空值缓存
     */
    private void setEmptyCache(String redisKey) {
        stringRedisTemplate.opsForValue().set(redisKey, RedisConstant.EMPTY_VALUE, RedisConstant.EMPTY_CACHE_TTL, TimeUnit.SECONDS);
    }

    /**
     * 随机TTL写入缓存（防雪崩）
     */
    private <T> void writeToCacheWithRandomTtl(String redisKey, T data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            long randomTtl = new Random().nextInt(600); // 0~10分钟随机
            stringRedisTemplate.opsForValue().set(redisKey, json, randomTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("写入缓存失败", e);
        }
    }

    /**
     * 从缓存获取或返回空
     */
    private <T> T getFromCacheOrNull(String redisKey, Class<T> clazz) {
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isNotBlank(cachedValue) && !RedisConstant.EMPTY_VALUE.equals(cachedValue)) {
            try {
                return objectMapper.readValue(cachedValue, clazz);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 简单哈希函数
     */
    private int hash(String str) {
        return Math.abs(str.hashCode() % 1048576);
    }

    @FunctionalInterface
    public interface OrderDbLoader<T> {

        /**
         * 从数据库加载数据
         * @return 数据对象，如果不存在返回 null
         */
        T load();
    }
}
