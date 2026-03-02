package com.stms.export;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.export.mapper")
@SpringBootApplication
public class exportApplication {

    public static void main(String[] args) {
        SpringApplication.run(com.stms.export.exportApplication.class, args);
        System.out.println("Server started");
    }
}
