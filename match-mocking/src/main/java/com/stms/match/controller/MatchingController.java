package com.stms.match.controller;

import com.stms.match.service.MatchService;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.common.domain.entity.quotation; // 导入quotation实体
import com.stms.quotation.mapper.quotationMapper; // 注入quotation Mapper
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    /**
     * 接收订单的接口（示例）
     */
    @PostMapping("/order")
    public String handleOrder(@RequestBody OrderDTO orderDTO) {
        // 1. 原有逻辑：调用 match 服务处理订单（会触发 syncTradeToDatabase）
        matchService.processOrder(orderDTO);

        // 2. 👇 新增逻辑：撮合后，在这里写回 quotation 表
        syncPriceToQuotation(orderDTO.getSecurityId(), orderDTO.getPrice());

        return "OK";
    }

    // 👇 3. 新增：把逻辑写在 Controller 里面
    private void syncPriceToQuotation(String securityId, Double latestPrice) {
        // 1. 先查是否有行情
        quotation quote = quotationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<quotation>()
                        .eq(quotation::getSecurityId, securityId)
        );

        if (quote != null) {
            // 2. 有记录，更新
            quotationMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<quotation>()
                            .eq(quotation::getSecurityId, securityId)
                            .set(quotation::getBidPrice, latestPrice)
                            .set(quotation::getAskPrice, latestPrice)
                            .set(quotation::getUpdateTime, LocalDateTime.now())
            );
        } else {
            // 3. 无记录，插入
            quotation newQuote = new quotation();
            newQuote.setSecurityId(securityId);
            newQuote.setMarket("SH"); // 填实际市场
            newQuote.setBidPrice(latestPrice);
            newQuote.setAskPrice(latestPrice);
            newQuote.setUpdateTime(LocalDateTime.now());
            quotationMapper.insert(newQuote);
        }
    }
}