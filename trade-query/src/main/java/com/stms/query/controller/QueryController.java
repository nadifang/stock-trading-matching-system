package com.stms.query.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stms.common.constant.SystemConstant;
import com.stms.query.domain.vo.OrderBookVO;
import com.stms.query.domain.vo.OrderDetailVO;
import com.stms.query.service.OrderHistoryService;
import com.stms.query.service.OrderQueryService;
import com.stms.common.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/trade-query")
public class QueryController {

    @Resource
    private OrderQueryService orderQueryService;
    @Resource
    private OrderHistoryService orderHistoryService;

    @GetMapping("/query")
    public OrderBookVO query(@RequestParam String securityId) {
        if (securityId == null)
            return null;
        log.info("query股票订单: {}", securityId);
        return orderQueryService.getOrderById(securityId);
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "securityId", required = false) String securityId,
            @RequestParam(value = "shareholderId", required = false) String shareholderId
    ){
        log.info("查询订单列表 current={}, pageSize={}, securityId={}, shareholderId={}",
                current, pageSize, securityId, shareholderId);
        Page<Order> page = orderQueryService.page(
                new Page<>(current, pageSize),
                new LambdaQueryWrapper<Order>()
                        .eq(StringUtils.isNotBlank(securityId), Order::getSecurityId, securityId)
                        .eq(StringUtils.isNotBlank(shareholderId), Order::getShareholderId, shareholderId)
                        .orderByDesc(Order::getCreateTime)
                        .orderByDesc(Order::getId)
        );
        List<OrderDetailVO> list = new ArrayList<>();
        for(Order order : page.getRecords()){
            OrderDetailVO orderDetailVO = new OrderDetailVO();
            BeanUtil.copyProperties(order, orderDetailVO);
            list.add(orderDetailVO);
        }
        return Map.of(
                "records", list,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pageSize", page.getSize()
        );
    }

    @GetMapping("/history")
    public Object getHistory(@RequestParam String securityId){
        if (securityId == null)
            return null;
        log.info("history股票订单: {}", securityId);
        return orderHistoryService.getHistory(securityId);
    }
}
