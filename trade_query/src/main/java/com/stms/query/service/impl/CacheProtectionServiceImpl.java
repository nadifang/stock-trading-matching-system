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
     */
    public <T> T getWithFullProtection(String clOrderId, OrderDbLoader<T> dbLoader, Class<T> clazz) {
        // 1. 布隆过滤器防穿透
        if (!checkBloomFilter(clOrderId)) {
            log.warn("布隆过滤器拦截非法订单ID：{}", clOrderId);
            return null;
        }

        // 2. 构建 Redis key
        String redisKey = RedisConstant.ORDER_STATUS_KEY + clOrderId;

        // 3. 尝试从 Redis 获取
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);

        // 4. 处理空值缓存（防穿透）
        if (cachedValue == null) {
            // 先设置空值标记，防止缓存穿透
            setEmptyCache(redisKey);
            return null;
        }

        // 5. 空值标记直接返回
        if (RedisConstant.EMPTY_VALUE.equals(cachedValue)) {
            return null;
        }

        // 6. 正常解析返回
        try {
            return objectMapper.readValue(cachedValue, clazz);
        } catch (Exception e) {
            log.error("缓存JSON解析失败，订单ID：{}", clOrderId, e);
            stringRedisTemplate.delete(redisKey);
            return null;
        }
    }

    /**
     * 带分布式锁的缓存查询（防击穿）
     */
    public <T> T getWithLock(String clOrderId, OrderDbLoader<T> dbLoader, Class<T> clazz) {
        String redisKey = RedisConstant.ORDER_STATUS_KEY + clOrderId;

        // 1. 先查缓存
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isNotBlank(cachedValue) && !RedisConstant.EMPTY_VALUE.equals(cachedValue)) {
            try {
                return objectMapper.readValue(cachedValue, clazz);
            } catch (Exception e) {
                log.error("缓存解析失败，订单ID：{}", clOrderId, e);
            }
        }

        // 2. 缓存未命中，尝试获取分布式锁
        String lockKey = "lock:order:query:" + clOrderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 最多等待 100ms，锁持有时间 10s
            boolean acquired = lock.tryLock(100, 10000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                // 获取锁失败，返回旧数据或空
                log.warn("获取分布式锁失败，订单ID：{}", clOrderId);
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
            log.error("获取分布式锁被中断，订单ID：{}", clOrderId, e);
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
     * 检查是否是热点订单
     */
    public boolean isHotOrder(String clOrderId) {
        String hotKey = "hot:order:" + clOrderId;
        Long count = stringRedisTemplate.opsForValue().increment(hotKey);
        if (count != null && count == 1) {
            // 首次访问，设置过期时间
            stringRedisTemplate.expire(hotKey, 60, TimeUnit.SECONDS);
        }
        return count != null && count >= RedisConstant.HOT_ORDER_THRESHOLD;
    }

    /**
     * 设置热点订单（延长TTL）
     */
    public void markAsHotOrder(String clOrderId) {
        String redisKey = RedisConstant.ORDER_STATUS_KEY + clOrderId;
        Boolean exists = stringRedisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            stringRedisTemplate.expire(redisKey, RedisConstant.HOT_ORDER_TTL, TimeUnit.SECONDS);
            log.info("订单标记为热点，订单ID：{}，TTL：{}秒", clOrderId, RedisConstant.HOT_ORDER_TTL);
        }
    }

    /**
     * 布隆过滤器检查
     */
    private boolean checkBloomFilter(String clOrderId) {
        // 这里调用你已有的布隆过滤器方法
        // return redisSecurityService.mightContainOrderId(clOrderId);
        // 简化实现：
        String bloomKey = "bloom:order:ids";
        return stringRedisTemplate.opsForValue().getBit(bloomKey, hash(clOrderId)) != null;
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
