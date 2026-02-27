package com.stms.query.controller;

import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.service.OrderQueryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/trade")
public class QueryController {

    @Resource
    private OrderQueryService orderQueryService;

    @GetMapping("/query")
    public OrderDetailVO query(@RequestParam String clOrderId) {
        if (clOrderId == null)
            return null;
        return new OrderDetailVO(orderQueryService.getOrderById(clOrderId));
    }
}
