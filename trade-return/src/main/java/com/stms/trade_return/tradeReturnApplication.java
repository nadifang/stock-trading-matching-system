package com.stms.trade_return;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.stms.trade_return.mapper")
public class tradeReturnApplication {
    public static void main(String[] args) {
        SpringApplication.run(tradeReturnApplication.class, args);
    }
}
