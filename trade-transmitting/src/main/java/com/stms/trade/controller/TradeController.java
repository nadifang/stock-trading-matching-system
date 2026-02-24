package com.stms.trade.controller;


import com.stms.trade.domain.dto.orderCanceledDTO;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.vo.Result;
import com.stms.trade.service.orderCanceledService;
import com.stms.trade.service.orderService;
import com.stms.trade.utils.CanceledResponse;
import com.stms.trade.utils.TradeRespose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/trade")
public class  TradeController {

    @Resource
    private orderService orderService;
    @Resource
    private orderCanceledService orderCanceledService;

    @PostMapping("/submit")
    public Result submitOrder(@RequestBody orderDTO order) {

        log.info("收到订单: {}", order);

        //1.提交订单
        TradeRespose tradeResponse = orderService.submitOrder(order);

        //2.返回结果
        if(tradeResponse.isSuccess()){
            return Result.ok(tradeResponse.getData());
        }
        else {
            return Result.fail(tradeResponse.getErrorMsg());
        }
    }

    @PostMapping("/cancel")
    public Result cancelOrder(@RequestBody orderCanceledDTO order) {
        log.info("收到取消订单: {}", order);

        //1.撤单
        CanceledResponse canceledResponse = orderCanceledService.cancelOrder(order);

        //2.返回撤单结果
        return Result.canceled_ok(canceledResponse.getData());
    }
}
