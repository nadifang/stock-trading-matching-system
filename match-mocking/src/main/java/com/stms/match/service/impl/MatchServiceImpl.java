package com.stms.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import com.stms.match.utils.MatchingOrder;
import com.stms.match.utils.OrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class MatchServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements MatchService {

    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String EARLY_CANCEL_KEY_PREFIX = "EARLY_CANCEL:";
    private static final long CANCEL_EXPIRY_SECONDS = 5;

    @Autowired
    private TransactionAsyncServiceImpl transactionAsyncService;

    @Override
    public void processOrder(OrderDTO order) {
        String securityId = order.getSecurityId();
        OrderBook book = orderBooks.computeIfAbsent(securityId, k -> new OrderBook());
        MatchingOrder matchingOrder = new MatchingOrder(order);

        boolean isCanceled = redisTemplate.delete(EARLY_CANCEL_KEY_PREFIX + order.getOrderId());
        if (isCanceled) {
            System.out.println("订单已被提前撤销 | 订单ID: " + order.getOrderId());
            return;
        }

        synchronized (book) {
            book.orderIndex.put(order.getOrderId(), matchingOrder);

            if ("B".equalsIgnoreCase(order.getSide())) {
                matchOrder(book, matchingOrder, true);
            } else if ("S".equalsIgnoreCase(order.getSide())) {
                matchOrder(book, matchingOrder, false);
            }
        }
    }

    @Override
    public boolean cancelOrder(CancelDTO cancelDTO) {
        OrderBook book = orderBooks.get(cancelDTO.getSecurityId());

        if (book == null) {
            return earlyCancelMark(cancelDTO.getOriginalOrderId());
        }

        synchronized (book) {
            MatchingOrder target = book.orderIndex.get(cancelDTO.getOriginalOrderId());
            if (target != null && target.getRemQty() > 0 && !target.isCanceled()) {
                target.setCanceled(true);
                book.orderIndex.remove(cancelDTO.getOriginalOrderId());
                return true;
            } else {
                return earlyCancelMark(cancelDTO.getOriginalOrderId());
            }
        }

    }

    private void matchOrder(OrderBook book, MatchingOrder takerOrder, boolean isBuy) {
        TreeMap<Double, LinkedList<MatchingOrder>> makerBook = isBuy ? book.sellBook : book.buyBook;

        while (takerOrder.getRemQty() > 0 && !makerBook.isEmpty()) {
            Map.Entry<Double, LinkedList<MatchingOrder>> bestEntry = makerBook.firstEntry();
            Double bestPrice = bestEntry.getKey();
            LinkedList<MatchingOrder> bestOrders = bestEntry.getValue();

            boolean priceMatch = isBuy ? takerOrder.getOrderDTO().getPrice() >= bestPrice : takerOrder.getOrderDTO().getPrice() <= bestPrice;
            if (!priceMatch) {
                break;
            }

            MatchingOrder makerOrder = bestOrders.peek();

            if (makerOrder == null) {
                makerBook.remove(bestPrice);
                continue;
            }

            if (makerOrder.isCanceled() || makerOrder.getRemQty() == 0) {
                bestOrders.poll();
                if (bestOrders.isEmpty()) {
                    makerBook.remove(bestPrice);
                }
                book.orderIndex.remove(makerOrder.getOrderDTO().getOrderId());
                continue;
            }

            executeTrade(takerOrder, makerOrder, isBuy);

            if (makerOrder.getRemQty() == 0) {
                bestOrders.poll();
                if (bestOrders.isEmpty()) {
                    makerBook.remove(bestPrice);
                }
                book.orderIndex.remove(makerOrder.getOrderDTO().getOrderId());
            }
        }

        if (takerOrder.getRemQty() > 0) {
            TreeMap<Double, LinkedList<MatchingOrder>> targetBook = isBuy ? book.buyBook : book.sellBook;
            targetBook.computeIfAbsent(takerOrder.getOrderDTO().getPrice(), k -> new LinkedList<>()).add(takerOrder);
        }else{
            book.orderIndex.remove(takerOrder.getOrderDTO().getOrderId());
        }
    }

    private boolean earlyCancelMark(String orderId) {
        String key = EARLY_CANCEL_KEY_PREFIX + orderId;
        redisTemplate.opsForValue().setIfAbsent(key, "1", CANCEL_EXPIRY_SECONDS, TimeUnit.SECONDS);
        return true;
    }

    private void executeTrade(MatchingOrder takerOrder, MatchingOrder makerOrder, boolean takerIsBuy) {
        Long execQty = Math.min(takerOrder.getRemQty(), makerOrder.getRemQty());
        Double execPrice = makerOrder.getOrderDTO().getPrice();

        takerOrder.setRemQty(takerOrder.getRemQty() - execQty);
        makerOrder.setRemQty(makerOrder.getRemQty() - execQty);

        MatchingOrder buyOrder = takerIsBuy ? takerOrder : makerOrder;
        MatchingOrder sellOrder = takerIsBuy ? makerOrder : takerOrder;

        Transaction transaction = buildTransaction(buyOrder, sellOrder, execQty, execPrice);
        transactionAsyncService.saveTransactionAsync(transaction);

        System.out.println("撮合成功 | 股票: " + transaction.getSecurityId() +
                " | 成交价: " + execPrice + " | 成交量: " + execQty +
                " | 买单ID: " + transaction.getBuyOrderId() + " | 卖单ID: " + transaction.getSellOrderId());
    }

    private Transaction buildTransaction(MatchingOrder buy, MatchingOrder sell, Long execQty, Double execPrice) {
        Transaction transaction = new Transaction();
        transaction.setExecId(generateTimeBasedExecId());
        transaction.setSecurityId(buy.getOrderDTO().getSecurityId());
        transaction.setExecPrice(execPrice);
        transaction.setExecQty(execQty);
        transaction.setBuyOrderId(buy.getOrderDTO().getOrderId());
        transaction.setSellOrderId(sell.getOrderDTO().getOrderId());
        transaction.setBuyerId(buy.getOrderDTO().getShareholderId());
        transaction.setSellerId(sell.getOrderDTO().getShareholderId());
        return transaction;
    }

    private String generateTimeBasedExecId() {
        // 1. 获取当前时间的毫秒数，并转为36进制（包含字母和数字，一般会生成约 8 位的字符串）
        String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();

        // 2. 计算还需要补齐多少位（12 - 时间部分的长度）
        int randomLen = 12 - timePart.length();

        // 3. 用随机大写字母和数字补齐剩下的位数
        StringBuilder sb = new StringBuilder(timePart);
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < randomLen; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }
}