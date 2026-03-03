package com.stms.shareholder.Utils;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.shareholder.domain.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 构造函数注入
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;  // 第一次访问，token 为空，放行到登录拦截器
        }

        // 2. 基于 token 获取 redis 中的用户（现在是 String 类型）
        String redisKey = RedisConstant.USER_LOGIN_TOKEN_KEY + token;
        String userJson = stringRedisTemplate.opsForValue().get(redisKey);

        // 3. 判断用户是否存在
        if (StrUtil.isBlank(userJson)) {
            log.error("用户未登录或 token 已过期，key: {}", redisKey);
            return true;  // 让登录拦截器处理
        }

        // 4. 将查询到的 JSON 字符串转化为 UserDTO 对象
        ObjectMapper objectMapper = new ObjectMapper();
        UserDTO userDTO;
        try {
            userDTO = objectMapper.readValue(userJson, UserDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Redis 中用户数据 JSON 解析失败", e);
            return true;
        }

        // 5. 存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 6. 刷新 token 有效期（注意 key 要和登录时一致）
        stringRedisTemplate.expire(redisKey, 30, TimeUnit.MINUTES);

        // 7. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除 ThreadLocal，防止内存泄漏
        UserHolder.removeUser();
    }
}