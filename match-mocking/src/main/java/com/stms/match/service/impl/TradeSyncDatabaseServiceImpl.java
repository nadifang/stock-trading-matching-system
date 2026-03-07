package com.stms.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.domain.entity.Order;
import com.stms.common.domain.entity.quotation;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.OrderMapper;
import com.stms.match.mapper.TransactionMapper;
import com.stms.quotation.mapper.quotationMapper;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.utils.MatchingOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class TradeSyncDatabaseServiceImpl implements TradeSyncDatabaseService {

    @Resource
    private TransactionMapper transactionMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private quotationMapper quotationMapper;

    @Transactional(rollbackFor = Exception.class)
    public void syncTradeToDatabase(MatchingOrder buyOrder, MatchingOrder sellOrder, Long execQty, Double execPrice, Transaction transaction) {

        boolean buySuccess = updateOrderDb(buyOrder, execQty, execPrice);
        if (!buySuccess) {
            throw new RuntimeException("Failed to update buy order in database");
        }

        boolean sellSuccess = updateOrderDb(sellOrder, execQty, execPrice);
        if (!sellSuccess) {
            throw new RuntimeException("Failed to update sell order in database");
        }

        transaction.setExecTime(LocalDateTime.now());
        transactionMapper.insert(transaction);

        syncPriceToQuotation(transaction.getSecurityId(), execPrice, buyOrder.getOrderDTO().getMarket());
    }

    private void syncPriceToQuotation(String securityId, Double latestPrice, String market) {
        // 先查该证券是否已有行情记录
        quotation quote = quotationMapper.selectOne(
                new LambdaQueryWrapper<quotation>()
                        .eq(quotation::getSecurityId, securityId)
        );

        if (quote != null) {
            // 有记录：更新买一价/卖一价为最新成交价，更新时间
            quotationMapper.update(
                    null,
                    new LambdaUpdateWrapper<quotation>()
                            .eq(quotation::getSecurityId, securityId)
                            .set(quotation::getBidPrice, latestPrice)  // 买一价 = 撮合成交价
                            .set(quotation::getAskPrice, latestPrice)  // 卖一价 = 撮合成交价
                            .set(quotation::getUpdateTime, LocalDateTime.now())
            );
        } else {
            // 无记录：插入新行情（避免quotation模块查不到）
            quotation newQuote = new quotation();
            newQuote.setSecurityId(securityId);
            newQuote.setMarket(market);  // 从订单取market，更准确
            newQuote.setBidPrice(latestPrice);
            newQuote.setAskPrice(latestPrice);
            newQuote.setUpdateTime(LocalDateTime.now());
            quotationMapper.insert(newQuote);
        }
    }

    private boolean updateOrderDb(MatchingOrder order, Long execQty, Double execPrice) {
        String orderId = order.getOrderDTO().getClOrderId();
        Long currentRemQty = order.getRemQty();

        String newStatus = (currentRemQty.equals(execQty)) ? OrderStatusConstant.ORDER_STATUS_FILLED : OrderStatusConstant.ORDER_STATUS_MATCHED;

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getClOrderId, orderId)
                .in(Order::getStatus, OrderStatusConstant.ORDER_STATUS_PENDING, OrderStatusConstant.ORDER_STATUS_MATCHED)
                .ge(Order::getRemainingQty, execQty)
                .setSql("remaining_qty = remaining_qty - " + execQty)
                .setSql("cum_qty = cum_qty + " + execQty)
                .setSql("traded_amount = traded_amount + " + (execQty * execPrice))
                .set(Order::getStatus, newStatus)
        );

        return rows > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncCancelToDatabase(String orderId, Long canceledQty) {
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getClOrderId, orderId)
                .ge(Order::getRemainingQty, canceledQty)
                // 增加已撤数量 (IFNULL防止数据库默认为null的情况报错)
                .setSql("canceled_qty = IFNULL(canceled_qty, 0) + " + canceledQty)
                // 扣减剩余数量
                .setSql("remaining_qty = remaining_qty - " + canceledQty)
        );
    }
}
