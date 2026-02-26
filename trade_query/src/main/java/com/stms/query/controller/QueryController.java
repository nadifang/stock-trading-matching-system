package com.stms.query.controller;

import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/trade")
public class QueryController {

    @Resource
    private OrderQueryService orderQueryService;

    @GetMapping("/query")
    public OrderDetailVO query(@PathVariable("clOrderId") String clOrderId) {
        if (clOrderId == null)
            return null;
        return new OrderDetailVO(orderQueryService.getOrderById(clOrderId));
    }
}
