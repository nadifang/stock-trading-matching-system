package com.stms.query.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.common.domain.entity.Order;
import com.stms.query.domain.dto.OrderAsk;
import com.stms.query.domain.dto.OrderBid;
import com.stms.query.domain.vo.OrderBookVO;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.mapper.queryMapper;
import com.stms.query.service.CacheProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
    public class CacheProtectionServiceImpl implements CacheProtectionService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private queryMapper queryMapper;


    /**
     * 布隆过滤器防穿透、空值缓存防穿透、分布式锁防击穿、热点识别、随机TTL防雪崩
     * 带分布式锁的缓存查询（防击穿）
     */

    public OrderBookVO getOrderBookWithLock(String securityId) {
        // 1. 先查缓存（ZSet）
        OrderBookVO orderBook = getOrderBookFromCache(securityId);
        if (orderBook != null) {
            // 热点识别：标记热点
            markAsHotOrder(securityId);
            return orderBook;
        }

        // 2. 缓存未命中，尝试获取分布式锁
        String lockKey = RedisConstant.DISTRIBUTED_LOCK_KEY + "orderbook:" + securityId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 最多等待 100ms，锁持有时间 10s
            boolean acquired = lock.tryLock(100, 10000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                // 获取锁失败，返回旧数据或空
                log.warn("获取盘口分布式锁失败，股票ID：{}", securityId);
                return getOrderBookFromCache(securityId);
            }

            // 3. 双重检查缓存
            orderBook = getOrderBookFromCache(securityId);
            if (orderBook != null) {
                markAsHotOrder(securityId);
                return orderBook;
            }

            // 4. 查询数据库
            OrderBookVO data = loadOrderBookFromDb(securityId);

            // 5. 写入缓存
            if (data != null) {
                writeOrderBookToCacheWithRandomTtl(securityId, data);
            } else {
                setEmptyOrderBookCache(securityId);
            }

            return data;

        } catch (InterruptedException e) {
            log.error("获取盘口锁被中断，股票ID：{}", securityId, e);
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从缓存获取盘口数据
     */
    /**
     * 从缓存获取盘口数据（有序）
     */
    private OrderBookVO getOrderBookFromCache(String securityId) {
        String askKey = RedisConstant.ORDER_BOOK_ASK_KEY + securityId;
        String bidKey = RedisConstant.ORDER_BOOK_BID_KEY + securityId;

        // 取卖价最低5（升序，ZSet 内部已排序）
        Set<String> askSet = stringRedisTemplate.opsForZSet().range(askKey, 0, 4);
        // 取买价最高5（降序，ZSet 内部已排序）
        Set<String> bidSet = stringRedisTemplate.opsForZSet().reverseRange(bidKey, 0, 4);

        // Set 转 List，保持顺序
        List<String> lowestAsks = askSet != null ? new ArrayList<>(askSet) : Collections.emptyList();
        List<String> highestBids = bidSet != null ? new ArrayList<>(bidSet) : Collections.emptyList();

        if (lowestAsks.isEmpty() && highestBids.isEmpty()) {
            return null;
        }

        OrderBookVO vo = new OrderBookVO();
        vo.setSecurityId(securityId);
        vo.setLowestAsks(lowestAsks);
        vo.setHighestBids(highestBids);
        return vo;
    }

    /**
     * 写入盘口缓存（带随机TTL防雪崩）
     */
    private void writeOrderBookToCacheWithRandomTtl(String securityId, OrderBookVO data) {
        String askKey = RedisConstant.ORDER_BOOK_ASK_KEY + securityId;
        String bidKey = RedisConstant.ORDER_BOOK_BID_KEY + securityId;

        // 清空旧数据
        stringRedisTemplate.delete(askKey);
        stringRedisTemplate.delete(bidKey);

        // 写入卖盘 ZSet（score = 卖价，自动按价格升序）
        if (data.getLowestAsks() != null) {
            for (String orderJson : data.getLowestAsks()) {
                try {
                    JsonNode node = objectMapper.readTree(orderJson);
                    double price = node.get("price").asDouble();
                    stringRedisTemplate.opsForZSet().add(askKey, orderJson, price);
                } catch (Exception e) {
                    log.error("解析卖盘订单失败", e);
                }
            }
        }

        // 写入买盘 ZSet（score = 买价，自动按价格降序取时用 reverseRange）
        if (data.getHighestBids() != null) {
            for (String orderJson : data.getHighestBids()) {
                try {
                    JsonNode node = objectMapper.readTree(orderJson);
                    double price = node.get("price").asDouble();
                    stringRedisTemplate.opsForZSet().add(bidKey, orderJson, price);
                } catch (Exception e) {
                    log.error("解析买盘订单失败", e);
                }
            }
        }

        // 基础TTL 3秒，加随机值（0~2秒）防雪崩
        long baseTtl = 3; // 秒
        long randomTtl = baseTtl + new Random().nextInt(3); // 3~5秒
        stringRedisTemplate.expire(askKey, randomTtl, TimeUnit.SECONDS);
        stringRedisTemplate.expire(bidKey, randomTtl, TimeUnit.SECONDS);
    }

    /**
     * 设置空值缓存（防穿透）
     */
    private void setEmptyOrderBookCache(String securityId) {
        String askKey = RedisConstant.ORDER_BOOK_ASK_KEY + securityId;
        String bidKey = RedisConstant.ORDER_BOOK_BID_KEY + securityId;
        // 空值用一个特殊标记
        stringRedisTemplate.opsForValue().set(askKey, RedisConstant.EMPTY_VALUE, RedisConstant.EMPTY_CACHE_TTL, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(bidKey, RedisConstant.EMPTY_VALUE, RedisConstant.EMPTY_CACHE_TTL, TimeUnit.SECONDS);
    }

    /**
     * 从数据库加载盘口（用你现有的 OrderDbLoader 模式）
     */
    private OrderBookVO loadOrderBookFromDb(String securityId) {
        // 用你现有的 OrderDbLoader 模式
        return new OrderDbLoader<OrderBookVO>() {
            @Override
            public OrderBookVO load() {
                OrderBookVO vo = new OrderBookVO();
                vo.setSecurityId(securityId);

                // 1. 查卖盘：价格升序，取最低5条
                List<OrderDetailVO> askList = queryMapper.selectLowest5(securityId);
                // 2. 查买盘：价格降序，取最高5条
                List<OrderDetailVO> bidList = queryMapper.selectHighest5(securityId);

                // 3. 转成 JSON 字符串列表（保持顺序）
                List<String> lowestAsks = askList.stream()
                        .map(this::toJson)
                        .collect(Collectors.toList()); // 注意：用 toList() 保持顺序

                List<String> highestBids = bidList.stream()
                        .map(this::toJson)
                        .collect(Collectors.toList()); // 注意：用 toList() 保持顺序

                vo.setLowestAsks(lowestAsks);
                vo.setHighestBids(highestBids);
                return vo;
            }

            private String toJson(Object obj) {
                try {
                    return objectMapper.writeValueAsString(obj);
                } catch (JsonProcessingException e) {
                    log.error("序列化订单失败", e);
                    return "{}";
                }
            }
        }.load();
    }



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
