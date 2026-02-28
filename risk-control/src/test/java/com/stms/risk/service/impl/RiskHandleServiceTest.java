package com.stms.risk.service.impl;

import com.stms.risk.entity.OrderEntity;
import com.stms.risk.mapper.OrderMapper;
import com.stms.risk.mapper.ShareholderGroupMapper;
import com.stms.risk.service.RiskReturnService;
import com.stms.risk.task.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskHandleServiceTest {

    @Mock
    private ShareholderGroupMapper shareholderGroupMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private RiskReturnService riskReturnService;

    @InjectMocks
    private RiskHandleServiceImpl riskHandleService;

    private OrderCreatedEvent event;

    @BeforeEach
    void setUp() {
        event = new OrderCreatedEvent();
        event.setClOrderId("ORD123");
        event.setShareholderId("SH001");
        event.setSide("BUY");
        event.setSecurityId("SEC001");
    }

    @Test
    void processOrder_WhenShareholderNotInAnyGroup_ShouldPass() {
        // 模拟股东不在任何组
        when(shareholderGroupMapper.findGroupIdByShareholderId("SH001")).thenReturn(null);

        riskHandleService.processOrder(event);

        // 验证风险成功处理被调用
        verify(riskReturnService).handleRiskSuccess(event);
        verify(riskReturnService, never()).handleRiskError(any(), anyString());
        verify(orderMapper, never()).findOppositeOrders(anyString(), anyString(), anyList());
    }

    @Test
    void processOrder_WhenGroupHasNoOppositeOrders_ShouldPass() {
        String groupId = "G001";
        List<String> shareholderIds = List.of("SH001", "SH002");

        when(shareholderGroupMapper.findGroupIdByShareholderId("SH001")).thenReturn(groupId);
        when(shareholderGroupMapper.findShareholderIdsByGroupId(groupId)).thenReturn(shareholderIds);
        when(orderMapper.findOppositeOrders("SEC001", "SELL", shareholderIds)).thenReturn(Collections.emptyList());

        riskHandleService.processOrder(event);

        verify(riskReturnService).handleRiskSuccess(event);
        verify(riskReturnService, never()).handleRiskError(any(), anyString());
    }

    @Test
    void processOrder_WhenOppositeOrderExists_ShouldReject() {
        String groupId = "G001";
        List<String> shareholderIds = List.of("SH001", "SH002");
        OrderEntity oppositeOrder = new OrderEntity();
        oppositeOrder.setClOrderId("ORD456");
        List<OrderEntity> oppositeOrders = List.of(oppositeOrder);

        when(shareholderGroupMapper.findGroupIdByShareholderId("SH001")).thenReturn(groupId);
        when(shareholderGroupMapper.findShareholderIdsByGroupId(groupId)).thenReturn(shareholderIds);
        when(orderMapper.findOppositeOrders("SEC001", "SELL", shareholderIds)).thenReturn(oppositeOrders);

        riskHandleService.processOrder(event);

        verify(riskReturnService).handleRiskError(eq(event), anyString());
        verify(riskReturnService, never()).handleRiskSuccess(any());
    }

    @Test
    void processOrder_WhenSideIsSell_ShouldLookForBuyOrders() {
        event.setSide("SELL");
        String groupId = "G001";
        List<String> shareholderIds = List.of("SH001", "SH002");

        when(shareholderGroupMapper.findGroupIdByShareholderId("SH001")).thenReturn(groupId);
        when(shareholderGroupMapper.findShareholderIdsByGroupId(groupId)).thenReturn(shareholderIds);
        when(orderMapper.findOppositeOrders("SEC001", "BUY", shareholderIds)).thenReturn(Collections.emptyList());

        riskHandleService.processOrder(event);

        verify(orderMapper).findOppositeOrders("SEC001", "BUY", shareholderIds);
        verify(riskReturnService).handleRiskSuccess(event);
    }
}