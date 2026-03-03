package com.stms.shareholder.controller;

import com.stms.shareholder.Service.QueryShareholderService;
import com.stms.shareholder.Service.UserService;
import com.stms.shareholder.domain.dto.LoginDTO;
import com.stms.shareholder.domain.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/User")
@Slf4j
public class ShareholderController {

    @Resource
    private UserService userService;
    @Resource
    private QueryShareholderService queryShareholderService;

    @PostMapping("/login")
    public Result getUser(@RequestBody LoginDTO loginForm) {
        return userService.login(loginForm);
    }

    @PostMapping("/getInfo")
    @Operation(summary = "获取公司股票信息")
    public Result logout(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("获取用户信息");
        return queryShareholderService.getInfo(token);
    }
}
