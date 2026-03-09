package com.stms.match.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.constant.RedisConstant;
import com.stms.common.constant.MatchLogConstant;
import com.stms.common.domain.entity.quotation;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import com.stms.match.service.TradeSyncDatabaseService;
import com.stms.match.utils.MatchingOrder;
import com.stms.match.utils.OrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MatchServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements MatchService {

    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private TradeSyncDatabaseService tradeSyncDatabaseService;


    @Override
    public void processOrder(OrderDTO order) {
        String orderJson = stringRedisTemplate.opsForValue().get(RedisConstant.ORDER_STATUS_KEY + order.getClOrderId());

        if (orderJson != null && !orderJson.trim().isEmpty()) {
            try {
                Map<String, Object> orderMap = objectMapper.readValue(orderJson, new TypeReference<>() {
                });

                String currentStatus = String.valueOf(orderMap.get("status"));

                if (OrderStatusConstant.ORDER_STATUS_CANCELED.equals(currentStatus) ||
                        OrderStatusConstant.ORDER_STATUS_REJECTED.equals(currentStatus)) {
                    log.warn(MatchLogConstant.MATCH_INTERCEPT_STATUS, currentStatus, order.getClOrderId());
                    return;
                }
            } catch (Exception e) {
                // 如果 JSON 解析发生异常，为了安全起见，拦截该订单并打印日志
                log.error(MatchLogConstant.MATCH_INTERCEPT_REDIS_ERROR, order.getClOrderId(), e.getMessage());
                return;
            }
        }

        String securityId = order.getSecurityId();
        OrderBook book = orderBooks.computeIfAbsent(securityId, k -> new OrderBook());
        MatchingOrder matchingOrder = new MatchingOrder(order);

        synchronized (book) {
            book.orderIndex.put(order.getClOrderId(), matchingOrder);

            if ("B".equalsIgnoreCase(order.getSide())) {
                matchOrder(book, matchingOrder, true);
            } else if ("S".equalsIgnoreCase(order.getSide())) {
                matchOrder(book, matchingOrder, false);
            }
        }
    }

    @Override
    public boolean cancelOrder(CancelDTO cancelDTO) {
        String orderId = cancelDTO.getOrigClOrderId();
        OrderBook book = orderBooks.get(cancelDTO.getSecurityId());

        if (book == null) {
            return true;
        }

        synchronized (book) {
            MatchingOrder target = book.orderIndex.get(cancelDTO.getOrigClOrderId());
            if (target != null && target.getRemQty() > 0 && !target.isCanceled()) {
                Long canceledQty = target.getRemQty();
                target.setCanceled(true);
                target.setRemQty(0L);
                book.orderIndex.remove(orderId);

                try {
                    // 1. 同步到数据库
                    tradeSyncDatabaseService.syncCancelToDatabase(orderId, canceledQty);
                    // 2. 同步更新Redis缓存
                    updateRedisAfterCancel(orderId, canceledQty);
                    return true;
                } catch (Exception e) {
                    log.error(MatchLogConstant.MATCH_SYNC_CANCEL_ERROR, orderId, e.getMessage());
                    return false;
                }
            }
            return false;
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
                book.orderIndex.remove(makerOrder.getOrderDTO().getClOrderId());
                continue;
            }

            executeTrade(takerOrder, makerOrder, isBuy);

            if (makerOrder.getRemQty() == 0) {
                bestOrders.poll();
                if (bestOrders.isEmpty()) {
                    makerBook.remove(bestPrice);
                }
                book.orderIndex.remove(makerOrder.getOrderDTO().getClOrderId());
            }
        }

        if (takerOrder.getRemQty() > 0) {
            TreeMap<Double, LinkedList<MatchingOrder>> targetBook = isBuy ? book.buyBook : book.sellBook;
            targetBook.computeIfAbsent(takerOrder.getOrderDTO().getPrice(), k -> new LinkedList<>()).add(takerOrder);
        } else {
            book.orderIndex.remove(takerOrder.getOrderDTO().getClOrderId());
        }
    }

    private void executeTrade(MatchingOrder takerOrder, MatchingOrder makerOrder, boolean takerIsBuy) {
        Long execQty = Math.min(takerOrder.getRemQty(), makerOrder.getRemQty());
        Double execPrice = makerOrder.getOrderDTO().getPrice();

        MatchingOrder buyOrder = takerIsBuy ? takerOrder : makerOrder;
        MatchingOrder sellOrder = takerIsBuy ? makerOrder : takerOrder;
        Transaction transaction = buildTransaction(buyOrder, sellOrder, execQty, execPrice);

        try {
            tradeSyncDatabaseService.syncTradeToDatabase(buyOrder, sellOrder, execQty, execPrice, transaction);

            takerOrder.setRemQty(takerOrder.getRemQty() - execQty);
            makerOrder.setRemQty(makerOrder.getRemQty() - execQty);
            log.info(MatchLogConstant.MATCH_EXECUTE_SUCCESS, transaction.getSecurityId(), execPrice, execQty);
            updateRedisAfterTrade(takerOrder, execQty, execPrice);
            updateRedisAfterTrade(makerOrder, execQty, execPrice);
            quotation quotations = new quotation();
            quotations.setSecurityId(transaction.getSecurityId());

            quotations.setBidPrice(execPrice); // 买价更新为成交价
            quotations.setAskPrice(execPrice); // 卖价更新为成交价
            // 市场（如果订单DTO中有市场字段，替换为真实值，这里先给默认值）
            quotations.setMarket(buyOrder.getOrderDTO().getMarket()); // 可改为 order.getMarket() 等真实字段
            // 更新时间：当前时间
            quotations.setUpdateTime(LocalDateTime.now());

            // 2. 调用价格同步函数
            tradeSyncDatabaseService.syncPriceToQuotation(quotations);

        } catch (Exception e) {
            log.error(MatchLogConstant.MATCH_EXECUTE_ROLLBACK, transaction.getSecurityId(), execPrice, execQty);
            takerOrder.setCanceled(true);
            makerOrder.setCanceled(true);
        }
    }

    private void updateRedisAfterTrade(MatchingOrder order, Long execQty, Double execPrice) {
        String statusKey = RedisConstant.ORDER_STATUS_KEY + order.getOrderDTO().getClOrderId();
        String orderJson = stringRedisTemplate.opsForValue().get(statusKey);

        if (orderJson != null && !orderJson.isEmpty()) {
            try {
                // 1. 解析 JSON 为 Map
                Map<String, Object> orderMap = objectMapper.readValue(orderJson, new TypeReference<>() {
                });

                // 2. 判断新状态
                String newStatus = (order.getRemQty() == 0)
                        ? OrderStatusConstant.ORDER_STATUS_FILLED
                        : OrderStatusConstant.ORDER_STATUS_MATCHED;

                // 3. 更新关键字段
                orderMap.put("status", newStatus);
                orderMap.put("remaining_qty", order.getRemQty());

                // 计算累计数量和累计金额 (注意数据类型转换，Redis反序列化后数字可能是Integer)
                long currentCumQty = Long.parseLong(String.valueOf(orderMap.getOrDefault("cum_qty", 0)));
                double currentTradedAmount = Double.parseDouble(String.valueOf(orderMap.getOrDefault("traded_amount", 0.0)));

                orderMap.put("cum_qty", currentCumQty + execQty);
                orderMap.put("traded_amount", currentTradedAmount + (execQty * execPrice));

                // 4. 将更新后的Map重新序列化为JSON，并写回Redis（重置过期时间为1天）
                String updatedOrderJson = objectMapper.writeValueAsString(orderMap);
                stringRedisTemplate.opsForValue().set(statusKey, updatedOrderJson, 1, TimeUnit.DAYS);

            } catch (Exception e) {
                log.error(MatchLogConstant.MATCH_REDIS_UPDATE_ERROR, order.getOrderDTO().getClOrderId(), e.getMessage());
            }
        }
    }

    private void updateRedisAfterCancel(String orderId, Long canceledQty) {
        String statusKey = RedisConstant.ORDER_STATUS_KEY + orderId;
        String orderJson = stringRedisTemplate.opsForValue().get(statusKey);

        if (orderJson != null && !orderJson.isEmpty()) {
            try {
                // 1. 解析 JSON 为 Map
                Map<String, Object> orderMap = objectMapper.readValue(orderJson, new TypeReference<>() {
                });

                // 2. 更新撤单相关字段
                orderMap.put("status", OrderStatusConstant.ORDER_STATUS_CANCELED);
                orderMap.put("remaining_qty", 0);

                long currentCanceledQty = Long.parseLong(String.valueOf(orderMap.getOrDefault("canceled_qty", 0)));
                orderMap.put("canceled_qty", currentCanceledQty + canceledQty);

                // 3. 写回Redis
                String updatedOrderJson = objectMapper.writeValueAsString(orderMap);
                stringRedisTemplate.opsForValue().set(statusKey, updatedOrderJson, 1, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error(MatchLogConstant.MATCH_REDIS_CANCEL_ERROR, orderId, e.getMessage());
            }
        }
    }

    private Transaction buildTransaction(MatchingOrder buy, MatchingOrder sell, Long execQty, Double execPrice) {
        Transaction transaction = new Transaction();
        transaction.setExecId(generateTimeBasedExecId());
        transaction.setSecurityId(buy.getOrderDTO().getSecurityId());
        transaction.setExecPrice(execPrice);
        transaction.setExecQty(execQty);
        transaction.setBuyOrderId(buy.getOrderDTO().getClOrderId());
        transaction.setSellOrderId(sell.getOrderDTO().getClOrderId());
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
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < randomLen; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }
}