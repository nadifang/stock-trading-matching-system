package com.stms.match.controller;

import com.stms.common.Util.Result;
import com.stms.match.service.MatchService;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.common.domain.entity.quotation; // 导入quotation实体
import com.stms.quotation.domain.dto.quotationDTO;
import com.stms.quotation.domain.vo.quotationVO;
import com.stms.match.mapper.quotationMapper; // 注入quotation Mapper
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/match")
public class MatchingController {

    @Resource
    private MatchService matchService;

    @Resource
    private TradeSyncDatabaseService tradeSyncDatabaseService;

    // 👇 1. 新增注入 quotation 的 Mapper 或 Service
    @Resource
    private quotationMapper quotationMapper;

    @PostMapping("/publishQuotation")
    public Result getQuotation(@RequestBody quotationDTO quotationDTO) {
        log.info("行情参数：{}", quotationDTO);
        tradeSyncDatabaseService.hadQuotation(quotationDTO);
        return null;
    }

    /**
     * 接收订单的接口（示例）
     */
    @PostMapping("/order")
    public String handleOrder(@RequestBody OrderDTO orderDTO) {
        // 1. 原有逻辑：调用 match 服务处理订单（会触发 syncTradeToDatabase）
        matchService.processOrder(orderDTO);
        return "OK";
    }


}