package com.stms.risk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.risk.mapper")
@SpringBootApplication
public class riskApplication {

    public static void main(String[] args) {
        SpringApplication.run(riskApplication.class, args);
    }

}
