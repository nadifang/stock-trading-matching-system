package com.stms.match.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.match.domain.dto.CancelDTO;
import com.stms.match.domain.dto.OrderDTO;
import com.stms.match.service.MatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchMessageListenerTest {

    @InjectMocks
    private MatchMessageListener matchMessageListener;

    @Mock
    private MatchService matchService;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试处理MQ订单消息：正常解析并调用处理")
    void testHandleOrderFromMQ_Success() throws Exception {
        // 1. 准备数据
        String validJson = "{\"clOrderId\":\"ORDER_123\",\"price\":10.0}";
        OrderDTO mockOrder = new OrderDTO();
        mockOrder.setClOrderId("ORDER_123");

        // 2. Mock Jackson 行为
        when(objectMapper.readValue(eq(validJson), eq(OrderDTO.class))).thenReturn(mockOrder);

        // 3. 执行
        matchMessageListener.handleOrderFromMQ(validJson);

        // 4. 验证 Service 被正确调用
        verify(matchService, times(1)).processOrder(mockOrder);
    }

    @Test
    @DisplayName("测试处理MQ订单消息：JSON解析异常被安全捕获")
    void testHandleOrderFromMQ_Exception() throws Exception {
        String invalidJson = "{invalid_json}";

        // Mock 抛出异常
        when(objectMapper.readValue(anyString(), eq(OrderDTO.class))).thenThrow(new RuntimeException("JSON Parse Error"));

        // 执行不会抛出异常（因为监听器里 try-catch 掉了）
        matchMessageListener.handleOrderFromMQ(invalidJson);

        // 验证业务方法不会被调用
        verify(matchService, never()).processOrder(any());
    }

    @Test
    @DisplayName("测试处理MQ撤单消息：正常解析并调用撤单")
    void testHandleCancelFromMQ_Success() throws Exception {
        // 1. 准备数据
        String validCancelJson = "{\"origClOrderId\":\"ORDER_123\"}";
        CancelDTO mockCancel = new CancelDTO();
        mockCancel.setOrigClOrderId("ORDER_123");

        // 2. Mock
        when(objectMapper.readValue(eq(validCancelJson), eq(CancelDTO.class))).thenReturn(mockCancel);
        when(matchService.cancelOrder(mockCancel)).thenReturn(true);

        // 3. 执行
        matchMessageListener.handleCancelFromMQ(validCancelJson);

        // 4. 验证
        verify(matchService, times(1)).cancelOrder(mockCancel);
    }
}