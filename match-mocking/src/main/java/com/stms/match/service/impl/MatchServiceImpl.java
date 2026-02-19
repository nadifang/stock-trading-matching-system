package com.stms.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class MatchServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements MatchService {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<OrderDTO>> buyQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<OrderDTO>> sellQueues = new ConcurrentHashMap<>();

    @Override
    public void processOrder(OrderDTO order) {
        String stockCode = order.getSecurityId();
        String orderId = order.getOrderId();
        String side = order.getSide();

        buyQueues.putIfAbsent(stockCode, new ConcurrentLinkedQueue<>());
        sellQueues.putIfAbsent(stockCode, new ConcurrentLinkedQueue<>());

        if ("BUY".equalsIgnoreCase(side)) {
            buyQueues.get(stockCode).offer(order);
            System.out.println("✅ 买单入队：订单ID=" + orderId + "，股票=" + stockCode + "，价格=" + order.getPrice());
        } else if ("SELL".equalsIgnoreCase(side)) {
            sellQueues.get(stockCode).offer(order);
            System.out.println("✅ 卖单入队：订单ID=" + orderId + "，股票=" + stockCode + "，价格=" + order.getPrice());
        }

        matchOrder(stockCode);
    }

    private void matchOrder(String stockCode) {
        ConcurrentLinkedQueue<OrderDTO> buyQueue = buyQueues.get(stockCode);
        ConcurrentLinkedQueue<OrderDTO> sellQueue = sellQueues.get(stockCode);

        while (!buyQueue.isEmpty() && !sellQueue.isEmpty()) {
            OrderDTO bestBuy = buyQueue.peek();
            OrderDTO bestSell = sellQueue.peek();

            if (bestBuy.getPrice().compareTo(bestSell.getPrice()) >= 0) {
                System.out.println("🔍 撮合成功！");
                System.out.println("   买单ID：" + bestBuy.getOrderId() + "，价格：" + bestBuy.getPrice());
                System.out.println("   卖单ID：" + bestSell.getOrderId() + "，价格：" + bestSell.getPrice());
                System.out.println("   成交价格：" + bestSell.getPrice() + "，成交数量：" + Math.min(bestBuy.getQty(), bestSell.getQty()));

                // 可选：解开下面注释，就能把成交记录存到数据库
                // Transaction transaction = buildTransaction(bestBuy, bestSell);
                // baseMapper.insert(transaction);

                buyQueue.poll();
                sellQueue.poll();
            } else {
                System.out.println("❌ 价格不匹配，等待新订单... 买价=" + bestBuy.getPrice() + " < 卖价=" + bestSell.getPrice());
                break;
            }
        }
    }

    private Transaction buildTransaction(OrderDTO buy, OrderDTO sell) {
        Transaction transaction = new Transaction();
        transaction.setExecId(System.currentTimeMillis() + "");
        transaction.setSecurityId(buy.getSecurityId());
        transaction.setExecPrice(sell.getPrice());
        transaction.setExecQty(Math.min(buy.getQty(), sell.getQty()));
        transaction.setBuyOrderId(buy.getOrderId());
        transaction.setSellOrderId(sell.getOrderId());
        transaction.setBuyerId(buy.getShareholderId());
        transaction.setSellerId(sell.getShareholderId());
        return transaction;
    }
}