package com.stms.trade.controller;


import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.vo.Result;
import com.stms.trade.service.orderService;
import com.stms.trade.utils.TradeRespose;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class TradeController {

    @Resource
    private orderService orderService;

    @PostMapping("/trade")
    public Result submitOrder(@RequestBody orderDTO order) {
        TradeRespose respose = orderService.submitOrder(order);
        if(respose.isSuccess()){
            return Result.ok(respose.getData());
        }
        else {
            return Result.fail(respose.getErrorMsg());
        }
    }
}
