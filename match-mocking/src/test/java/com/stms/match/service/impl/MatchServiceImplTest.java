package com.stms.match.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.service.TradeSyncDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @InjectMocks
    private MatchServiceImpl matchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TradeSyncDatabaseService tradeSyncDatabaseService;

    private static final String DEFAULT_SECURITY_ID = "000001";

    @BeforeEach
    void setUp() {
        // Mock Redis 操作，返回 null，使得程序跳过 Redis 状态拦截和更新，专注于测试撮合算法本身
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("测试完全撮合：买卖价格、数量完全一致")
    void testProcessOrder_ExactMatch() {
        // 1. 提交买单：10元买入100股
        OrderDTO buyOrder = createOrder("BUY_1", "B", 10.0, 100L);
        matchService.processOrder(buyOrder);

        // 2. 提交卖单：10元卖出100股 (Taker)
        OrderDTO sellOrder = createOrder("SELL_1", "S", 10.0, 100L);
        matchService.processOrder(sellOrder);

        // 3. 验证数据库同步服务被调用了1次，且成交数量为100，成交价格为10.0 (取Maker价)
        verify(tradeSyncDatabaseService, times(1)).syncTradeToDatabase(
                any(), any(), eq(100L), eq(10.0), any()
        );
    }

    @Test
    @DisplayName("测试部分撮合：买单数量大于卖单数量")
    void testProcessOrder_PartialMatch() {
        // 1. 提交买单：10元买入200股 (Maker)
        OrderDTO buyOrder = createOrder("BUY_1", "B", 10.0, 200L);
        matchService.processOrder(buyOrder);

        // 2. 提交卖单：10元卖出100股 (Taker)
        OrderDTO sellOrder = createOrder("SELL_1", "S", 10.0, 100L);
        matchService.processOrder(sellOrder);

        // 3. 验证发生了100股的成交
        verify(tradeSyncDatabaseService, times(1)).syncTradeToDatabase(
                any(), any(), eq(100L), eq(10.0), any()
        );

        // 4. 再提交一个卖单：10元卖出100股，验证买单剩余的100股能否继续成交
        OrderDTO sellOrder2 = createOrder("SELL_2", "S", 10.0, 100L);
        matchService.processOrder(sellOrder2);

        verify(tradeSyncDatabaseService, times(2)).syncTradeToDatabase(
                any(), any(), eq(100L), eq(10.0), any()
        );
    }

    @Test
    @DisplayName("测试价格优先原则与价格交叉（Taker价格优于Maker）")
    void testProcessOrder_PriceImprovement() {
        // 1. 提交卖单：10元卖出100股 (Maker)
        OrderDTO sellOrder = createOrder("SELL_1", "S", 10.0, 100L);
        matchService.processOrder(sellOrder);

        // 2. 提交买单：10.5元买入100股 (Taker)
        OrderDTO buyOrder = createOrder("BUY_1", "B", 10.5, 100L);
        matchService.processOrder(buyOrder);

        // 3. 验证成交价格应为 Maker 价格 (10.0元)
        verify(tradeSyncDatabaseService, times(1)).syncTradeToDatabase(
                any(), any(), eq(100L), eq(10.0), any()
        );
    }

    @Test
    @DisplayName("测试价格不匹配：不会发生撮合")
    void testProcessOrder_NoMatch() {
        // 1. 提交买单：9.0元买入100股
        OrderDTO buyOrder = createOrder("BUY_1", "B", 9.0, 100L);
        matchService.processOrder(buyOrder);

        // 2. 提交卖单：10.0元卖出100股
        OrderDTO sellOrder = createOrder("SELL_1", "S", 10.0, 100L);
        matchService.processOrder(sellOrder);

        // 3. 验证没有任何撮合发生
        verify(tradeSyncDatabaseService, never()).syncTradeToDatabase(
                any(), any(), anyLong(), anyDouble(), any()
        );
    }

    @Test
    @DisplayName("测试撤单功能")
    void testCancelOrder() {
        // 1. 提交买单
        OrderDTO buyOrder = createOrder("BUY_1", "B", 10.0, 100L);
        matchService.processOrder(buyOrder);

        // 2. 发起撤单
        CancelDTO cancelDTO = new CancelDTO();
        cancelDTO.setOrigClOrderId("BUY_1");
        cancelDTO.setSecurityId(DEFAULT_SECURITY_ID);
        boolean cancelResult = matchService.cancelOrder(cancelDTO);

        assertTrue(cancelResult, "撤单应该返回true");

        // 3. 提交一个本可以成交的卖单测试刚才的买单是否真的被撤销
        OrderDTO sellOrder = createOrder("SELL_1", "S", 10.0, 100L);
        matchService.processOrder(sellOrder);

        // 4. 验证没有发生交易（因为买单已被撤销）
        verify(tradeSyncDatabaseService, never()).syncTradeToDatabase(
                any(), any(), anyLong(), anyDouble(), any()
        );
    }

    /**
     * 构建订单的辅助方法
     */
    private OrderDTO createOrder(String clOrderId, String side, Double price, Long qty) {
        OrderDTO order = new OrderDTO();
        order.setClOrderId(clOrderId);
        order.setMarket("SH");
        order.setSecurityId(DEFAULT_SECURITY_ID);
        order.setSide(side);
        order.setPrice(price);
        order.setQty(qty);
        order.setShareholderId("SH0001");
        return order;
    }
}