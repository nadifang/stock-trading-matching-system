package com.stms.match;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.constant.RedisConstant;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.common.domain.entity.Order;
import com.stms.match.domain.entity.Transaction;
import com.stms.match.mapper.OrderMapper;
import com.stms.match.mapper.TransactionMapper;
import com.stms.match.service.MatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class MatchTest {

    @Autowired
    private MatchService matchService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 使用时间戳生成动态订单号，防止多次运行测试导致数据库单号重复报错
    private final String currentTime = String.valueOf(System.currentTimeMillis());
    private final String BUY_ORDER_ID = "BUY_T_" + currentTime;
    private final String SELL_ORDER_ID = "SELL_T_" + currentTime;
    private final String SECURITY_ID = "600000";

    @Test
    @DisplayName("全流程测试：真实DB与Redis集成")
    void testFullProcessMatchingKeepData() throws Exception {
        /* ==============================================
         * 1. 初始化真实的数据库数据 (模拟风控审核后的落库)
         * ============================================== */
        // 插入买单 (数量 200)
        Order dbBuyOrder = new Order();
        dbBuyOrder.setClOrderId(BUY_ORDER_ID);
        dbBuyOrder.setSecurityId(SECURITY_ID);
        dbBuyOrder.setMarket("XSHG");
        dbBuyOrder.setSide("B");
        dbBuyOrder.setShareholderId("2000000");
        dbBuyOrder.setQty(200L);
        dbBuyOrder.setPrice(10.5);
        dbBuyOrder.setRemainingQty(200L);
        dbBuyOrder.setCumQty(0L);
        dbBuyOrder.setTradedAmount(0L);
        dbBuyOrder.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);
        orderMapper.insert(dbBuyOrder);

        // 插入卖单 (数量 100)
        Order dbSellOrder = new Order();
        dbSellOrder.setClOrderId(SELL_ORDER_ID);
        dbSellOrder.setSecurityId(SECURITY_ID);
        dbSellOrder.setMarket("XSHG");
        dbSellOrder.setSide("S");
        dbSellOrder.setShareholderId("100000");
        dbSellOrder.setQty(100L);
        dbSellOrder.setPrice(10.5);
        dbSellOrder.setRemainingQty(100L);
        dbSellOrder.setCumQty(0L);
        dbSellOrder.setTradedAmount(0L);
        dbSellOrder.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);
        orderMapper.insert(dbSellOrder);

        /* ==============================================
         * 2. 初始化真实的 Redis 数据 (模拟网关或风控写入的状态)
         * ============================================== */
        Map<String, Object> buyRedisMap = new HashMap<>();
        buyRedisMap.put("clOrderId", BUY_ORDER_ID);
        buyRedisMap.put("status", OrderStatusConstant.ORDER_STATUS_PENDING);
        buyRedisMap.put("remaining_qty", 200);
        buyRedisMap.put("cum_qty", 0);
        buyRedisMap.put("traded_amount", 0.0);
        stringRedisTemplate.opsForValue().set(RedisConstant.ORDER_STATUS_KEY + BUY_ORDER_ID, objectMapper.writeValueAsString(buyRedisMap));

        Map<String, Object> sellRedisMap = new HashMap<>();
        sellRedisMap.put("clOrderId", SELL_ORDER_ID);
        sellRedisMap.put("status", OrderStatusConstant.ORDER_STATUS_PENDING);
        sellRedisMap.put("remaining_qty", 100);
        sellRedisMap.put("cum_qty", 0);
        sellRedisMap.put("traded_amount", 0.0);
        stringRedisTemplate.opsForValue().set(RedisConstant.ORDER_STATUS_KEY + SELL_ORDER_ID, objectMapper.writeValueAsString(sellRedisMap));

        /* ==============================================
         * 3. 构造请求并触发真实的撮合引擎逻辑
         * ============================================== */
        OrderDTO buyDTO = new OrderDTO();
        buyDTO.setClOrderId(BUY_ORDER_ID);
        buyDTO.setSecurityId(SECURITY_ID);
        buyDTO.setMarket("XSHG");
        buyDTO.setSide("B");
        buyDTO.setQty(200L);
        buyDTO.setPrice(10.5);
        buyDTO.setShareholderId("2000000");

        OrderDTO sellDTO = new OrderDTO();
        sellDTO.setClOrderId(SELL_ORDER_ID);
        sellDTO.setSecurityId(SECURITY_ID);
        sellDTO.setMarket("XSHG");
        sellDTO.setSide("S");
        sellDTO.setQty(100L);
        sellDTO.setPrice(10.5);
        sellDTO.setShareholderId("100000");

        // 提交订单进入撮合池（由于买单200卖单100，所以卖单会全部成交，买单部分成交）
        matchService.processOrder(buyDTO);
        matchService.processOrder(sellDTO);

        /* ==============================================
         * 4. 验证真实数据库与 Redis 中的最终状态
         * ============================================== */

        // --- 验证数据库 (DB) ---
        Order afterBuyDb = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getClOrderId, BUY_ORDER_ID));
        assertEquals(100L, afterBuyDb.getRemainingQty(), "买单应剩余100股");
        assertEquals(100L, afterBuyDb.getCumQty(), "买单已成交100股");
        assertEquals(OrderStatusConstant.ORDER_STATUS_MATCHED, afterBuyDb.getStatus(), "买单应为部分成交状态");

        Order afterSellDb = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getClOrderId, SELL_ORDER_ID));
        assertEquals(0L, afterSellDb.getRemainingQty(), "卖单应剩余0股");
        assertEquals(100L, afterSellDb.getCumQty(), "卖单已完全成交");
        assertEquals(OrderStatusConstant.ORDER_STATUS_FILLED, afterSellDb.getStatus(), "卖单应为完全成交状态");

        Transaction dbTx = transactionMapper.selectOne(new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getBuyOrderId, BUY_ORDER_ID)
                .eq(Transaction::getSellOrderId, SELL_ORDER_ID));
        assertNotNull(dbTx, "必须生成一条成交流水记录");
        assertEquals(100L, dbTx.getExecQty(), "成交流水数量应为100");
        assertEquals(10.5, dbTx.getExecPrice(), "成交流水价格应为10.5");

        // --- 验证 Redis 缓存 ---
        String buyRedisAfter = stringRedisTemplate.opsForValue().get(RedisConstant.ORDER_STATUS_KEY + BUY_ORDER_ID);
        Map<String, Object> buyAfterMap = objectMapper.readValue(buyRedisAfter, new TypeReference<>() {});
        assertEquals(OrderStatusConstant.ORDER_STATUS_MATCHED, buyAfterMap.get("status"), "Redis中买单状态应更新");
        assertEquals(100L, Long.parseLong(String.valueOf(buyAfterMap.get("remaining_qty"))));
        assertEquals(100L, Long.parseLong(String.valueOf(buyAfterMap.get("cum_qty"))));
        assertEquals(1050.0, Double.parseDouble(String.valueOf(buyAfterMap.get("traded_amount"))));

        String sellRedisAfter = stringRedisTemplate.opsForValue().get(RedisConstant.ORDER_STATUS_KEY + SELL_ORDER_ID);
        Map<String, Object> sellAfterMap = objectMapper.readValue(sellRedisAfter, new TypeReference<>() {});
        assertEquals(OrderStatusConstant.ORDER_STATUS_FILLED, sellAfterMap.get("status"), "Redis中卖单状态应更新");
        assertEquals(0L, Long.parseLong(String.valueOf(sellAfterMap.get("remaining_qty"))));
        assertEquals(100L, Long.parseLong(String.valueOf(sellAfterMap.get("cum_qty"))));
        assertEquals(1050.0, Double.parseDouble(String.valueOf(sellAfterMap.get("traded_amount"))));

        // 测试结束后，控制台会打印生成的订单号，你可以直接去 DB 或 Redis 里搜索它们
        System.out.println("=================================================");
        System.out.println("测试执行完毕，你可以前往数据库或 Redis 检查以下订单状态：");
        System.out.println("买单 ID: " + BUY_ORDER_ID);
        System.out.println("卖单 ID: " + SELL_ORDER_ID);
        System.out.println("成交流水中的 buy_order_id 和 sell_order_id 对应以上两个 ID");
        System.out.println("=================================================");
    }
}