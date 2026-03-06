package com.stms.quotation;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@MapperScan("com.stms.quotation.mapper")
@SpringBootApplication
public class quotationApplication {
    public static void main(String[] args) {
        SpringApplication.run(quotationApplication.class, args);
        log.info("Server started");
    }
}
