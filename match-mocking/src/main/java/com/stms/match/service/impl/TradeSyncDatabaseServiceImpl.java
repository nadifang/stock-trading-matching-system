package com.stms.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.domain.entity.Order;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.OrderMapper;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.utils.MatchingOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class TradeSyncDatabaseServiceImpl implements TradeSyncDatabaseService {

    @Resource
    private TransactionMapper transactionMapper;

    @Resource
    private OrderMapper orderMapper;

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

        transactionMapper.insert(transaction);
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
