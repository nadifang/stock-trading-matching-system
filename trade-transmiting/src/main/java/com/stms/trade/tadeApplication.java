package com.stms.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.trade.mapper")
@SpringBootApplication
public class tadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(tadeApplication.class, args);
    }

}
