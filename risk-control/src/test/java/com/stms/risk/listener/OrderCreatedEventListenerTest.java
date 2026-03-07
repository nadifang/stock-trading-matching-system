//package com.stms.risk.listener;
//
//import com.stms.risk.service.RiskHandleService;
//import com.stms.risk.task.OrderCreatedEvent;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class OrderCreatedEventListenerTest {
//
//    @Mock
//    private RiskHandleService riskHandleService;
//
//    @InjectMocks
//    private OrderCreatedEventListener listener;
//
//    @Test
//    void handleOrderCreated_ShouldCallService() {
//        OrderCreatedEvent event = new OrderCreatedEvent();
//        event.setClOrderId("ORD123");
//
//        listener.handleOrderCreated(event);
//
//        verify(riskHandleService).processOrder(event);
//    }
//
//    @Test
//    void handleOrderCreated_WhenServiceThrowsException_ShouldLogError() {
//        OrderCreatedEvent event = new OrderCreatedEvent();
//        doThrow(new RuntimeException("Test exception")).when(riskHandleService).processOrder(event);
//
//        // 这里仅验证方法不会抛出异常（被catch了）
//        listener.handleOrderCreated(event);
//
//        verify(riskHandleService).processOrder(event);
//        // 可进一步验证日志输出，但需要结合日志框架 Mock，此处略
//    }
//}