package com.stms.risk.mytest;


import com.stms.risk.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class Itest {

    @Resource
    private OrderMapper orderMapper;

    @Test
    public void test() {
        System.out.println(orderMapper.findOppositeOrders("600387", "B", "1234567890"));
    }
}
