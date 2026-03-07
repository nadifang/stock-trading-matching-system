package com.stms.risk.service.impl;

import com.stms.risk.mapper.OrderMapper;
import com.stms.risk.mapper.ShareholderGroupMapper;
import com.stms.risk.service.RiskHandleService;
import com.stms.risk.service.RiskReturnService;
import com.stms.trade.task.OrderCreatedEvent;
import com.stms.risk.entity.OrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class RiskHandleServiceImpl implements RiskHandleService {

    @Autowired
    private ShareholderGroupMapper shareholderGroupMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RiskReturnService riskReturnService;

    @Override
    @Transactional
    public void processOrder(OrderCreatedEvent event) {
        log.info("Processing order: {}", event);

        String shareholderId = event.getShareholderId();

        // 1. 查询股东所属组
        //String groupId = shareholderGroupMapper.findGroupIdByShareholderId(shareholderId);
//        if (groupId == null) {
//            log.info("Shareholder {} not in any group, order passed", shareholderId);
//            passToNextModule(event);
//            return;
//        }

        // 2. 获取组内所有股东ID
        //List<String> shareholderIdsInGroup = shareholderGroupMapper.findShareholderIdsByGroupId(groupId);

        // 3. 确定反向方向
        String oppositeSide = event.getSide().equalsIgnoreCase("B") ? "S" : "B";

        // 4. 查询是否存在反向订单（同一证券、方向相反、状态为NEW/PARTIAL、股东在组内）
        boolean oppositeOrders = orderMapper.findOppositeOrders(
                event.getSecurityId(), oppositeSide, shareholderId);

        if (oppositeOrders) {
            String errorMsg = String.format("订单 %s存在自买自卖风险",
                    event.getClOrderId());
            log.warn(errorMsg);
            riskReturnService.handleRiskError(event, errorMsg);
            return;
        }

        // 5. 无风险，传递至下一模块
        log.info("Order {} passed risk check", event.getClOrderId());
        passToNextModule(event);
    }

    private void passToNextModule(OrderCreatedEvent event) {
        // 实际中应发送到下一模块的队列，例如通过 RabbitTemplate 发送到 trade.return 模块
        riskReturnService.handleRiskSuccess(event);
    }
}