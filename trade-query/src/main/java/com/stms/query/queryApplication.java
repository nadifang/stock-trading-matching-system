package com.stms.query;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.query.mapper")
@SpringBootApplication
public class queryApplication {

    public static void main(String[] args) {
        SpringApplication.run(queryApplication.class, args);
        System.out.println("Server started");
    }
}
