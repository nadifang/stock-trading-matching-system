package com.stms.shareholder.Service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.common.constant.UserConstant;
import com.stms.shareholder.Service.UserService;
import com.stms.shareholder.domain.dto.LoginDTO;
import com.stms.common.Util.Result;
import com.stms.shareholder.domain.dto.UserDTO;
import com.stms.shareholder.domain.entity.User;
import com.stms.shareholder.mapper.userMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<userMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result login(LoginDTO loginForm) {

        //1. 检验股东号格式
        String shareholderId = loginForm.getShareholderId();
        if(shareholderId == null || shareholderId.length() != 10){
            return Result.fail(UserConstant.SHAREHOLDERID_FORMAT_ERROR);
        }

        //2. 检验密码格式
        String password = loginForm.getPassword();
        if(password == null || !password.matches("^[a-zA-Z0-9]{6,20}$")){//英文字母（大小写）+数字,不能包含：中文、下划线、特殊符号、空格等,长度：6～20个字符
            return Result.fail(UserConstant.PASSWORD_FORMAT_ERROR);
        }

        //3. 检验用户
        User user = query().eq("shareholderId", shareholderId).one();
        if(user == null){
            user = createUserWithPhone(shareholderId);
            return Result.fail(UserConstant.USER_NOT_EXIST);
        }

        //4. 检验密码
        if(!user.getPassword().equals(password)){
            return Result.fail(UserConstant.PASSWORD_ERROR);
        }

        // 5. 保存用户信息到 redis 中，随机生成 token 作为登录令牌
        String token = UUID.randomUUID().toString();

        // 5.1 将 User 对象转为 JSON 字符串存储（String 类型）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String userJson;
        try {
            userJson = objectMapper.writeValueAsString(userDTO);
        } catch (JsonProcessingException e) {
            log.error("用户对象转 JSON 失败", e);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        // 5.2 用 String 类型保存 token 对应的用户信息
        stringRedisTemplate.opsForValue().set(
                RedisConstant.USER_LOGIN_TOKEN_KEY + token,
                userJson,
                30,
                TimeUnit.MINUTES
        );

        log.info("用户登录成功，生成令牌：{}", token);
        return Result.ok(token);
    }


    private User createUserWithPhone(String shareholderId) {
        User user = new User();
        user.setShareholderId(shareholderId);
        user.setPassword(UserConstant.DEFAULT_PASSWORD);
        user.setNickName(UserConstant.DEFAULT_NICK_NAME+ RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        return user;
    }

}
