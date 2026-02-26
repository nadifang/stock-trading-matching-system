package com.stms.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.domain.entity.Order;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.OrderMapper;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.utils.MatchingOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional // 核心注解：测试完成后自动回滚，不产生真实的数据库脏数据
class TradeSyncDatabaseServiceImplTest {

    @Autowired
    private TradeSyncDatabaseServiceImpl tradeSyncDatabaseService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Test
    @DisplayName("真实DB集成测试：撮合成功落库，验证数量扣减和状态变更")
    void testSyncTradeToDatabase_RealDb() {
        // ---------------- 1. 准备真实的数据库前置数据 ----------------
        String buyOrderId = "REAL_BUY_001";
        String sellOrderId = "REAL_SELL_001";
        String securityId = "600000";

        // 在数据库中插入一笔买单 (挂单状态，数量200)
        Order dbBuyOrder = new Order();
        dbBuyOrder.setClOrderId(buyOrderId);
        dbBuyOrder.setSecurityId(securityId);
        dbBuyOrder.setMarket("XSHG");
        dbBuyOrder.setSide("B");
        dbBuyOrder.setShareholderId("2000000");
        dbBuyOrder.setQty(200L);
        dbBuyOrder.setRemainingQty(200L); // 初始剩余200
        dbBuyOrder.setCumQty(0L);
        dbBuyOrder.setTradedAmount(0L);
        dbBuyOrder.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);
        orderMapper.insert(dbBuyOrder);

        // 在数据库中插入一笔卖单 (挂单状态，数量100)
        Order dbSellOrder = new Order();
        dbSellOrder.setClOrderId(sellOrderId);
        dbSellOrder.setSecurityId(securityId);
        dbSellOrder.setMarket("XSHG");
        dbSellOrder.setSide("S");
        dbSellOrder.setShareholderId("100000");
        dbSellOrder.setQty(100L);
        dbSellOrder.setRemainingQty(100L); // 初始剩余100
        dbSellOrder.setCumQty(0L);
        dbSellOrder.setTradedAmount(0L);
        dbSellOrder.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);
        orderMapper.insert(dbSellOrder);


        // ---------------- 2. 构造传给 Service 的内存对象 ----------------
        MatchingOrder buyMatchOrder = createMatchingOrder(buyOrderId, securityId, 200L);
        MatchingOrder sellMatchOrder = createMatchingOrder(sellOrderId, securityId, 100L);

        // 模拟撮合了100股，价格为 10.5
        Transaction transaction = new Transaction();
        transaction.setExecId("EXEC_REAL_12");
        transaction.setSecurityId(securityId);
        transaction.setMarket("XSHG");
        transaction.setExecQty(100L);
        transaction.setExecPrice(10.5);
        transaction.setBuyOrderId(buyOrderId);
        transaction.setSellOrderId(sellOrderId);


        // ---------------- 3. 执行真实数据库同步 ----------------
        tradeSyncDatabaseService.syncTradeToDatabase(buyMatchOrder, sellMatchOrder, 100L, 10.5, transaction);


        // ---------------- 4. 去真实数据库查询并断言结果 ----------------
        // 验证买单：应该部分成交 (剩余100，累计成交100)
        Order afterBuyOrder = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getClOrderId, buyOrderId));
        assertEquals(100L, afterBuyOrder.getRemainingQty(), "买单剩余数量应该被扣减100");
        assertEquals(100L, afterBuyOrder.getCumQty(), "买单累计成交数量应为100");
        assertEquals(1050L, afterBuyOrder.getTradedAmount(), "买单累计成交金额应为 100 * 10.5 = 1050");
        assertEquals(OrderStatusConstant.ORDER_STATUS_MATCHED, afterBuyOrder.getStatus(), "买单还有剩余，应为部分成交状态");

        // 验证卖单：应该完全成交 (剩余0，累计成交100)
        Order afterSellOrder = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getClOrderId, sellOrderId));
        assertEquals(0L, afterSellOrder.getRemainingQty(), "卖单剩余数量应为0");
        assertEquals(100L, afterSellOrder.getCumQty(), "卖单累计成交数量应为100");
        assertEquals(OrderStatusConstant.ORDER_STATUS_FILLED, afterSellOrder.getStatus(), "卖单已空，应为完全成交状态");

        // 验证成交流水 (Transaction) 是否成功插入
        Transaction dbTransaction = transactionMapper.selectOne(new LambdaQueryWrapper<Transaction>().eq(Transaction::getExecId, "EXEC_REAL_12"));
        assertNotNull(dbTransaction, "真实数据库中应能查到刚插入的成交流水");
        assertEquals(100L, dbTransaction.getExecQty());
        assertEquals(10.5, dbTransaction.getExecPrice());
    }

    @Test
    @DisplayName("真实DB集成测试：验证撤单落库逻辑")
    void testSyncCancelToDatabase_RealDb() {
        String orderId = "REAL_CANCEL_001";

        // 1. 先准备一个待撤销的订单 (总数200，之前成交过50，还剩150)
        Order dbOrder = new Order();
        dbOrder.setClOrderId(orderId);
        dbOrder.setQty(200L);
        dbOrder.setMarket("XSHG");
        dbOrder.setSecurityId("600000");
        dbOrder.setShareholderId("1000000");
        dbOrder.setSide("B");
        dbOrder.setRemainingQty(150L);
        dbOrder.setCumQty(50L);
        dbOrder.setCanceledQty(0L);
        dbOrder.setStatus(OrderStatusConstant.ORDER_STATUS_MATCHED); // 部分成交状态
        orderMapper.insert(dbOrder);

        // 2. 执行撤单动作：撤销剩余的 150 股
        tradeSyncDatabaseService.syncCancelToDatabase(orderId, 150L);

        // 3. 去数据库查询断言
        Order afterCancelOrder = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getClOrderId, orderId));
        assertEquals(0L, afterCancelOrder.getRemainingQty(), "剩余数量应被扣减为0");
        assertEquals(150L, afterCancelOrder.getCanceledQty(), "已撤数量应变为150");
    }

    /**
     * 辅助构造 MatchingOrder
     */
    private MatchingOrder createMatchingOrder(String clOrderId, String securityId, Long remQty) {
        OrderDTO dto = new OrderDTO();
        dto.setClOrderId(clOrderId);
        dto.setSecurityId(securityId);
        MatchingOrder order = new MatchingOrder(dto);
        order.setRemQty(remQty);
        return order;
    }
}