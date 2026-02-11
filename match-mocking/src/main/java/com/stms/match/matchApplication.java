package com.stms.match;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.match.mapper")
@SpringBootApplication
public class matchApplication {

    public static void main(String[] args) {
        SpringApplication.run(matchApplication.class, args);
    }

}
