package com.stms.query.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stms.common.constant.SystemConstant;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.service.OrderQueryService;
import com.stms.trade.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/trade-query")
public class QueryController {

    @Resource
    private OrderQueryService orderQueryService;

    @GetMapping("/query")
    public OrderDetailVO query(@RequestParam String clOrderId) {
        if (clOrderId == null)
            return null;
        log.info("查询订单: {}", clOrderId);
        return new OrderDetailVO(orderQueryService.getOrderById(clOrderId));
    }

    @GetMapping("/list")
    public List<OrderDetailVO> list(@RequestParam(value = "current", defaultValue = "1") Integer current){
        log.info("查询从第{}页开始的10条数据", current);
        Page<Order> page = orderQueryService.page(new Page<>(current, 10));
        List<OrderDetailVO> list = new ArrayList<>();
        for(Order order : page.getRecords()){
            OrderDetailVO orderDetailVO = new OrderDetailVO();
            BeanUtil.copyProperties(order, orderDetailVO);
            list.add(orderDetailVO);
        }
        return list;
    }
}
