package com.stms.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.trade.mapper")
@SpringBootApplication
public class tradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(tradeApplication.class, args);
        System.out.println("Server started");
    }

}
