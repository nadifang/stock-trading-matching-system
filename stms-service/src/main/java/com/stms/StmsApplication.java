package com.stms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.mapper")
@SpringBootApplication
class StmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StmsApplication.class, args);
    }

}
