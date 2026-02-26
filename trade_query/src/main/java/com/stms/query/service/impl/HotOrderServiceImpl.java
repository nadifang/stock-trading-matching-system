package com.stms.query.service.impl;

import com.stms.query.service.CacheProtectionService;
import com.stms.query.service.HotOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotOrderServiceImpl implements HotOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheProtectionService cacheProtectionService;

    // 热点订单访问计数 key 前缀
    private static final String HOT_ORDER_COUNT_PREFIX = "hot:order:count:";
    // 热点订单标记 key 前缀
    private static final String HOT_ORDER_MARK_PREFIX = "hot:order:mark:";
    // 统计窗口（1分钟）
    private static final int WINDOW_SECONDS = 60;
    // 热点阈值
    private static final int HOT_THRESHOLD = 10;
    // 热点 TTL（2小时）
    private static final long HOT_TTL = 7200;

    /**
     * 记录订单访问，并检查是否成为热点
     */
    public void recordAccess(String clOrderId) {
        String countKey = HOT_ORDER_COUNT_PREFIX + clOrderId;
        String markKey = HOT_ORDER_MARK_PREFIX + clOrderId;

        // 1. 增加访问计数
        Long count = stringRedisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1) {
            // 首次访问，设置过期时间
            stringRedisTemplate.expire(countKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        // 2. 检查是否达到热点阈值
        if (count != null && count >= HOT_THRESHOLD) {
            // 3. 标记为热点
            markAsHotOrder(clOrderId, markKey);
            // 4. 通知缓存保护服务
            cacheProtectionService.markAsHotOrder(clOrderId);
            // 5. 删除计数（避免重复标记）
            stringRedisTemplate.delete(countKey);
            log.info("订单成为热点，订单ID：{}，访问次数：{}", clOrderId, count);
        }
    }

    /**
     * 标记订单为热点
     */
    private void markAsHotOrder(String clOrderId, String markKey) {
        stringRedisTemplate.opsForValue().set(markKey, "1", HOT_TTL, TimeUnit.SECONDS);
    }

    /**
     * 检查订单是否是热点
     */
    public boolean isHotOrder(String clOrderId) {
        String markKey = HOT_ORDER_MARK_PREFIX + clOrderId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(markKey));
    }

    /**
     * 获取热点订单列表
     */
    public List<String> getHotOrderIds() {
        // 这里简化处理，实际需要扫描 key
        // 可以使用 Redis SCAN 命令
        Set<String> keys = stringRedisTemplate.keys(HOT_ORDER_MARK_PREFIX + "*");
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> k.replace(HOT_ORDER_MARK_PREFIX, ""))
                .collect(Collectors.toList());
    }
}
