package com.stms.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.domain.entity.Order;
import com.stms.common.domain.entity.quotation;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.OrderMapper;
import com.stms.match.mapper.TransactionMapper;
import com.stms.quotation.domain.dto.quotationDTO;
import com.stms.quotation.domain.vo.quotationVO;
import com.stms.match.mapper.quotationMapper;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.utils.MatchingOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class TradeSyncDatabaseServiceImpl extends ServiceImpl<quotationMapper,quotation> implements TradeSyncDatabaseService{

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

        transaction.setExecTime(LocalDateTime.now());
        transactionMapper.insert(transaction);

        //syncPriceToQuotation();
    }

    public void syncPriceToQuotation(quotation quotations) {
        // 先查该证券是否已有行情记录
        quotation quote = lambdaQuery().eq(quotation::getSecurityId, quotations.getSecurityId()).one();

        if (quote != null) {
            // 有记录：更新买一价/卖一价为最新成交价，更新时间
            lambdaUpdate().eq(quotation::getSecurityId, quotations.getSecurityId())
                    .set(quotation::getBidPrice, quotations.getBidPrice())
                    .set(quotation::getAskPrice, quotations.getAskPrice())
                    .set(quotation::getUpdateTime, LocalDateTime.now())
                    .update();
        } else {
            // 无记录：插入新行情（避免quotation模块查不到）
            save(quotations);
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

    @Override
    public void hadQuotation(quotationDTO quotationDTO) {
        quotation quotations = convertoQuotation(quotationDTO);
        syncPriceToQuotation(quotations);
    }

    public quotation getQuotationBySecurityId(String securityId) {
        return lambdaQuery().eq(quotation::getSecurityId, securityId).one();
    }

    private quotation convertoQuotation(quotationDTO quotationDTO) {
        quotation quotations = new quotation();
        String securityId = (quotationDTO.getSecurityId() != null) ? new String(quotationDTO.getSecurityId()) : null;
        String market = (quotationDTO.getMarket() != null) ? new String(quotationDTO.getMarket()) : null;
        quotations.setSecurityId(securityId);
        quotations.setMarket(market);
        quotations.setBidPrice(quotationDTO.getBidPrice());
        quotations.setAskPrice(quotationDTO.getAskPrice());
        quotations.setUpdateTime(LocalDateTime.now());
        return quotations;
    }
}
