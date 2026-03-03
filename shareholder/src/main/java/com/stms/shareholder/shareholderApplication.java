package com.stms.shareholder;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.stms.shareholder.mapper")
@SpringBootApplication
@Slf4j
public class shareholderApplication {
    public static void main(String[] args) {
        SpringApplication.run(shareholderApplication.class, args);
        log.info("Server started");
    }
}
