package com.stms.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.constant.RedisConstant;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import com.stms.match.utils.MatchingOrder;
import com.stms.match.utils.OrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements MatchService {

    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final DefaultRedisScript<Long> TRADE_PUSH_SCRIPT;

    static {
        TRADE_PUSH_SCRIPT = new DefaultRedisScript<>();
        TRADE_PUSH_SCRIPT.setLocation(new ClassPathResource("trade_check_and_push.lua"));
        TRADE_PUSH_SCRIPT.setResultType(Long.class);
    }

    @Override
    public void processOrder(OrderDTO order) {
        String currentStatus = redisTemplate.opsForValue().get(RedisConstant.ORDER_STATUS_KEY + order.getOrderId());
        if (OrderStatusConstant.ORDER_STATUS_CANCELED.equals(currentStatus)) {
            System.out.println("订单在Redis中已撤销，放弃进入撮合池 | 订单ID: " + order.getOrderId());
            return;
        }

        String securityId = order.getSecurityId();
        OrderBook book = orderBooks.computeIfAbsent(securityId, k -> new OrderBook());
        MatchingOrder matchingOrder = new MatchingOrder(order);

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
        String orderId = cancelDTO.getOriginalOrderId();
        OrderBook book = orderBooks.get(cancelDTO.getSecurityId());

        if (book == null) {
            return true;
        }

        synchronized (book) {
            MatchingOrder target = book.orderIndex.get(cancelDTO.getOriginalOrderId());
            if (target != null && target.getRemQty() > 0 && !target.isCanceled()) {
                target.setCanceled(true);
                book.orderIndex.remove(orderId);
            }
            return true;
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
        } else {
            book.orderIndex.remove(takerOrder.getOrderDTO().getOrderId());
        }
    }

    private void executeTrade(MatchingOrder takerOrder, MatchingOrder makerOrder, boolean takerIsBuy) {
        Long execQty = Math.min(takerOrder.getRemQty(), makerOrder.getRemQty());
        Double execPrice = makerOrder.getOrderDTO().getPrice();

        takerOrder.setRemQty(takerOrder.getRemQty() - execQty);
        makerOrder.setRemQty(makerOrder.getRemQty() - execQty);

        MatchingOrder buyOrder = takerIsBuy ? takerOrder : makerOrder;
        MatchingOrder sellOrder = takerIsBuy ? makerOrder : takerOrder;
        Transaction transaction = buildTransaction(buyOrder, sellOrder, execQty, execPrice);

        try {
            String transactionJson = objectMapper.writeValueAsString(transaction);

            // 2. 获取买卖双方的全局状态Key
            String takerStatusKey = RedisConstant.ORDER_STATUS_KEY + takerOrder.getOrderDTO().getOrderId();
            String makerStatusKey = RedisConstant.ORDER_STATUS_KEY + makerOrder.getOrderDTO().getOrderId();

            // 3. 执行Lua脚本：原子校验全局状态并推入队列
            Long result = redisTemplate.execute(
                    TRADE_PUSH_SCRIPT,
                    Arrays.asList(takerStatusKey, makerStatusKey, RedisConstant.TRANSACTION_QUEUE_KEY),
                    transactionJson,
                    OrderStatusConstant.ORDER_STATUS_CANCELED
            );

            // 4. 判断结果：成功则Redis托管，失败则回滚
            if (result == 1) {
                System.out.println("撮合成功并推入Redis | 股票: " + transaction.getSecurityId() +
                        " | 成交价: " + execPrice + " | 成交量: " + execQty);
            } else {
                // 回滚内存数据
                takerOrder.setRemQty(takerOrder.getRemQty() + execQty);
                makerOrder.setRemQty(makerOrder.getRemQty() + execQty);
                System.out.println("拦截：全局状态已撤单，撮合操作回滚 | 买单: " + transaction.getBuyOrderId() + " 卖单: " + transaction.getSellOrderId());
            }
        } catch (JsonProcessingException e) {
            takerOrder.setRemQty(takerOrder.getRemQty() + execQty);
            makerOrder.setRemQty(makerOrder.getRemQty() + execQty);
            System.out.println("撮合序列化异常，已回滚：" + e.getMessage());
        }
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