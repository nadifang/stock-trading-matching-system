package com.stms.query.service.impl;

import com.stms.common.constant.RedisConstant;
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

    /**
     * 记录股票访问，并检查是否成为热点
     */
    public void recordAccess(String securityId) {
        String countKey = RedisConstant.HOT_SECURITY_COUNT_PREFIX + securityId;
        String markKey = RedisConstant.HOT_SECURITY_MARK_PREFIX + securityId;

        // 1. 增加访问计数
        Long count = stringRedisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1) {
            // 首次访问，设置过期时间
            stringRedisTemplate.expire(countKey, RedisConstant.WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        // 2. 检查是否达到热点阈值
        if (count != null && count >= RedisConstant.HOT_THRESHOLD) {
            // 3. 标记为热点
            markAsHotOrder(securityId, markKey);
            // 4. 通知缓存保护服务
            cacheProtectionService.markAsHotOrder(securityId);
            // 5. 删除计数（避免重复标记）
            stringRedisTemplate.delete(countKey);
            log.info("股票成为热点，股票ID：{}，访问次数：{}", securityId, count);
        }
    }

    /**
     * 标记订单为热点
     */
    private void markAsHotOrder(String securityId, String markKey) {
        stringRedisTemplate.opsForValue().set(markKey, "1", RedisConstant.HOT_TTL, TimeUnit.SECONDS);
    }

    /**
     * 检查订单是否是热点
     */
    public boolean isHotOrder(String clOrderId) {
        String markKey = RedisConstant.HOT_SECURITY_MARK_PREFIX + clOrderId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(markKey));
    }

    /**
     * 获取热点订单列表
     */
    public List<String> getHotOrderIds() {
        // 这里简化处理，实际需要扫描 key
        // 可以使用 Redis SCAN 命令
        Set<String> keys = stringRedisTemplate.keys(RedisConstant.HOT_SECURITY_MARK_PREFIX + "*");
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> k.replace(RedisConstant.HOT_SECURITY_MARK_PREFIX, ""))
                .collect(Collectors.toList());
    }
}
