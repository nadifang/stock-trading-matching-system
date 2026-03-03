package com.stms.shareholder.Utils;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.shareholder.domain.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 无 Token，返回未登录
            response.setStatus(401);
            response.getWriter().write("用户未登录，拒绝访问");
            return false;
        }

        // 2. 拼接 Redis Key（和 login 时一致）
        String redisKey = RedisConstant.USER_LOGIN_TOKEN_KEY + token;

        // 3. 从 Redis 获取 JSON 字符串（String 类型）
        String userJson = stringRedisTemplate.opsForValue().get(redisKey);

        // 4. 判断用户是否存在
        if (StrUtil.isBlank(userJson)) {
            // Token 无效，返回未登录
            response.setStatus(401);
            response.getWriter().write("用户未登录，拒绝访问");
            return false;
        }

        // 5. 将 JSON 字符串转为 UserDTO
        UserDTO userDTO;
        try {
            userDTO = objectMapper.readValue(userJson, UserDTO.class);
        } catch (IOException e) {
            log.error("Redis 中用户数据 JSON 解析失败", e);
            response.setStatus(401);
            response.getWriter().write("用户数据解析失败，拒绝访问");
            return false;
        }

        // 6. 保存到 ThreadLocal
        UserHolder.saveUser(userDTO);
        log.info("用户登录状态验证成功，userDTO: {}", userDTO);

        // 7. 刷新 Token 有效期
        stringRedisTemplate.expire(redisKey, 30, TimeUnit.MINUTES);

        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除 ThreadLocal，防止内存泄漏
        UserHolder.removeUser();
    }
}