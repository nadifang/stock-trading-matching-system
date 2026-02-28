package com.stms.dotest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan("com.stms.dotest.mapper")
@SpringBootApplication
@EnableRabbit
public class detestApplication {
    public static void main(String[] args) {
        SpringApplication.run(detestApplication.class, args);
        System.out.println("Server started");
    }

}
