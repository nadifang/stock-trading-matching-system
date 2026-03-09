package com.stms.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.common.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.RedisSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisSecurityServiceImpl extends ServiceImpl<orderMapper, Order> implements RedisSecurityService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;


    // ==================== 短时间内重复订单ID打入 ====================

    public boolean isDuplicateOrderRequest(String clOrderId) {
        if (StringUtils.isBlank(clOrderId)) {
            return true; // 空ID直接放行，由后续校验拦截
        }

        long now = System.currentTimeMillis() / 1000; // 当前秒级时间戳
        String windowKey = RedisConstant.DUPLICATE_ORDER_BF_KEY + (now / RedisConstant.DUPLICATE_WINDOW_SECONDS); // 按时间窗口分组
        int hash1 = Math.abs(clOrderId.hashCode());
        int hash2 = Math.abs((clOrderId + "salt1").hashCode());
        int hash3 = Math.abs((clOrderId + "salt2").hashCode());

        // 计算在 Bitmap 中的位置
        int pos1 = hash1 % RedisConstant.BF_SIZE;
        int pos2 = hash2 % RedisConstant.BF_SIZE;
        int pos3 = hash3 % RedisConstant.BF_SIZE;

        // 使用 Redis BITOP 或分别 SETBIT
        // 这里用分别 SETBIT 的方式，避免 BITOP 的复杂
        Boolean bit1 = stringRedisTemplate.opsForValue().getBit(windowKey, pos1);
        Boolean bit2 = stringRedisTemplate.opsForValue().getBit(windowKey, pos2);
        Boolean bit3 = stringRedisTemplate.opsForValue().getBit(windowKey, pos3);

        // 如果三个位都已经为 1，说明在这个时间窗口内已经出现过
        if (Boolean.TRUE.equals(bit1) && Boolean.TRUE.equals(bit2) && Boolean.TRUE.equals(bit3)) {
            log.warn("检测到短时间内重复订单请求，clOrderId={}", clOrderId);
            return true; // 这里返回 true 表示“是重复”，业务层要拦截
        }

        // 否则，标记为已出现
        stringRedisTemplate.opsForValue().setBit(windowKey, pos1, true);
        stringRedisTemplate.opsForValue().setBit(windowKey, pos2, true);
        stringRedisTemplate.opsForValue().setBit(windowKey, pos3, true);

        // 设置这个窗口 key 的过期时间（窗口秒数 + 1，防止残留）
        stringRedisTemplate.expire(windowKey, RedisConstant.DUPLICATE_WINDOW_SECONDS + 1, TimeUnit.SECONDS);

        return false; // 不是重复
    }


    // ==================== 缓存穿透防护 ====================

    /**
     * 布隆过滤器：检查订单ID是否可能存在
     */
    public boolean mightContainOrderId(String clOrderId) {
        if (StringUtils.isBlank(clOrderId)) {
            return false;
        }
        // 简单实现：用 Redis BitMap 模拟布隆过滤器
        // 生产环境建议使用 RedisBloom 模块或 Guava BloomFilter
        try {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().getBit(RedisConstant.BLOOM_FILTER_KEY, clOrderId.hashCode())
            );
        } catch (Exception e) {
            log.error("布隆过滤器检查失败，订单ID：{}", clOrderId, e);
            return true; // 出错时放行，避免误杀
        }
    }

    /**
     * 添加订单ID到布隆过滤器
     */
    public void addOrderIdToBloomFilter(String clOrderId) {
        if (StringUtils.isNotBlank(clOrderId)) {
            stringRedisTemplate.opsForValue().setBit(RedisConstant.BLOOM_FILTER_KEY, clOrderId.hashCode(), true);
        }
    }

    /**
     * 带穿透+击穿防护的更新订单状态（创建/撤单通用）
     *
     * @param clOrderId  订单ID
     * @param status     新状态
     * @return 是否成功
     */
    public boolean updateOrderStatusWithProtection(String clOrderId, String status) {
        // 1. 布隆过滤器校验
        if (!mightContainOrderId(clOrderId)) {
            log.warn("布隆过滤器拦截非法订单ID：{}", clOrderId);
            return false;
        }

        // 2. 构建Redis key
        String redisKey = RedisConstant.ORDER_STATUS_KEY + clOrderId;

        // 3. 获取分布式锁（防止击穿）
        String lockKey = "lock:order:status:" + clOrderId;
        boolean locked = false;
        try {
            locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstant.LOCK_TTL, TimeUnit.SECONDS);

            if (locked) {
                // 4. 双重检查：再次从Redis获取
                String currentJson = stringRedisTemplate.opsForValue().get(redisKey);
                if (currentJson != null && !RedisConstant.EMPTY_VALUE_STRING.equals(currentJson)) {
                    try {
                        Map<String, Object> orderMap = objectMapper.readValue(currentJson, new TypeReference<Map<String, Object>>() {});
                        // 如果状态已经是目标状态，直接返回
                        if (status.equals(orderMap.get("status"))) {
                            return true;
                        }
                    } catch (Exception e) {
                        log.error("双重检查时JSON解析失败，订单ID：{}", clOrderId, e);
                    }
                }

                // 5. 执行数据库更新
                boolean dbSuccess = lambdaUpdate().eq(Order::getClOrderId, clOrderId).set(Order::getStatus, status).update();
                if (!dbSuccess) {
                    log.error("数据库更新订单状态失败，订单ID：{}", clOrderId);
                    return false;
                }

                // 6. 更新Redis（带随机TTL，防止雪崩）
                long randomTtl = getRandomTtl();
                updateRedisOrderStatus(redisKey, clOrderId, status, randomTtl);

                // 7. 添加到布隆过滤器（如果不存在）
                addOrderIdToBloomFilter(clOrderId);

                return true;
            } else {
                // 未获取到锁，短暂等待后重试（最多3次）
                for (int i = 0; i < 3; i++) {
                    Thread.sleep(50);
                    // 检查是否已经更新成功
                    String checkJson = stringRedisTemplate.opsForValue().get(redisKey);
                    if (checkJson != null && !RedisConstant.EMPTY_VALUE_STRING.equals(checkJson)) {
                        try {
                            Map<String, Object> orderMap = objectMapper.readValue(checkJson, new TypeReference<Map<String, Object>>() {});
                            if (status.equals(orderMap.get("status"))) {
                                return true;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                log.warn("更新订单状态时获取锁失败，订单ID：{}", clOrderId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("更新订单状态时线程被中断，订单ID：{}", clOrderId, e);
            return false;
        } finally {
            // 释放锁
            if (locked) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    /**
     * 更新Redis中的订单状态
     */
    private void updateRedisOrderStatus(String redisKey, String clOrderId, String status, long ttlSeconds) {
        try {
            // 先获取现有数据
            String currentJson = stringRedisTemplate.opsForValue().get(redisKey);
            Map<String, Object> orderMap;

            if (currentJson != null && !RedisConstant.EMPTY_VALUE_STRING.equals(currentJson)) {
                orderMap = objectMapper.readValue(currentJson, new TypeReference<Map<String, Object>>() {});
            } else {
                // 如果Redis中没有，从数据库查询完整订单
                // 这里简化处理，实际应调用dbLoader
                orderMap = new HashMap<>();
                Order order = lambdaQuery().eq(Order::getClOrderId, clOrderId).one();
                orderMap.put("clOrderId", order.getClOrderId());
                orderMap.put("market", order.getMarket());
                orderMap.put("securityId", order.getSecurityId());
                orderMap.put("side", order.getSide());
                orderMap.put("qty", order.getQty());
                orderMap.put("price", order.getPrice());
                orderMap.put("shareholderId", order.getShareholderId());
                orderMap.put("cumQty", order.getCumQty());
                orderMap.put("canceledQty", order.getCanceledQty());
                orderMap.put("remainingQty", order.getRemainingQty());
                orderMap.put("tradedAmount", order.getTradedAmount());
            }

            // 更新状态
            orderMap.put("status", status);

            // 写入Redis
            String updatedJson = objectMapper.writeValueAsString(orderMap);
            stringRedisTemplate.opsForValue().set(redisKey, updatedJson, ttlSeconds, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("更新Redis订单状态失败，订单ID：{}", clOrderId, e);
        }
    }

    /**
     * 生成随机TTL（基础1天 + 随机0-10分钟）
     */
    private long getRandomTtl() {
        long baseTtl = 86400; // 1天
        long randomMinutes = new Random().nextInt(RedisConstant.RANDOM_TTL_MINUTES);
        return baseTtl + randomMinutes * 60;
    }
}
