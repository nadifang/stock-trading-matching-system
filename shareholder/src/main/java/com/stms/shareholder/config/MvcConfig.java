package com.stms.shareholder.config;

import com.stms.shareholder.Utils.LoginInterceptor;
import com.stms.shareholder.Utils.RefreshTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Slf4j
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册 RefreshTokenInterceptor（负责解析 Token，刷新有效期，放入 ThreadLocal）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns(   // 排除不需要解析 Token 的路径
                        "/User/login",  // 登录接口
                        "/doc.html",    // Knife4j 文档页面
                        "/v3/api-docs", // OpenAPI 描述接口
                        "/webjars/**",  // Knife4j 静态资源
                        "/swagger-resources/**" // Swagger 资源
                );

        // 2. 注册 AuthInterceptor（负责校验登录态，拦截未登录请求）
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns(   // 排除不需要登录的路径（和上面保持一致）
                        // Swagger/Knife4j 相关
                        "/doc.html",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/knife4j/**",
                        // 登录接口（允许匿名访问）
                        "/User/login",
                        // 其他公开接口（如注册、验证码等）
                        "/User/register",
                        "/common/captcha"
                );
    }
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        log.info("开始添加拦截器");
//        registry.addInterceptor(new LoginInterceptor())
//                .excludePathPatterns(
//                        "/User/login",
//                        "/User/register",
//                        // Swagger 相关
//                        "/doc.html",
//                        "/webjars/**",
//                        "/v3/api-docs/**",
//                        "/swagger-ui/**",
//                        "/swagger-ui.html",
//                        // Knife4j 相关
//                        "/knife4j/**"
//                ).order(1);
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
//    }
}
