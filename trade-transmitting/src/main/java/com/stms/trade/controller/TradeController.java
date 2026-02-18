package com.stms.trade.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.vo.Result;
import com.stms.trade.service.orderService;
import com.stms.trade.utils.TradeRespose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
public class TradeController {

    @Resource
    private orderService orderService;

    @PostMapping("/trade")
    public Result submitOrder(@RequestBody orderDTO order) {

        log.info("收到订单: {}", order);

        //1.提交订单
        TradeRespose respose = orderService.submitOrder(order);

        //2.返回结果
        if(respose.isSuccess()){
            return Result.ok(respose.getData());
        }
        else {
            return Result.fail(respose.getErrorMsg());
        }
    }
}
