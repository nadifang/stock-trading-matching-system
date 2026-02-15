package com.stms.trade;

import cn.hutool.core.bean.BeanUtil;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.domain.vo.Result;
import com.stms.trade.service.impl.orderServiceImpl;
import com.stms.trade.utils.OrderCommand;
import com.stms.trade.utils.TradeRespose;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

@SpringBootTest
public class tradeApplicationTests {

    @Test
    public void testtradesubmit(){
        orderDTO order = new orderDTO();
        order.setClOrderId(new char[] {'1','2','3','4','5','6','7','8','9','8','7','6','5','4','3','2'});
        order.setMarket(new char[] {'X','S','H','G'});
        order.setSecurityId(new char[] {'6','0','0','0','0','3'});
        order.setSide(new char[] {'B'});
        order.setQty(100L);
        order.setPrice(10.0);
        order.setShareholderId(new char[] {'1','2','3','4','5','6','7','8','9','0'});
        //订单格式化转换
        Order orderCommand = convertToOrderCommand(order);

        System.out.println("*".repeat(30));

        System.out.println(orderCommand.getClOrderId());
        System.out.println(orderCommand.getMarket());
        System.out.println(orderCommand.getSecurityId());
        System.out.println(orderCommand.getSide());
        System.out.println(orderCommand.getQty());
        System.out.println(orderCommand.getPrice());
        System.out.println(orderCommand.getShareholderId());

        System.out.println("*".repeat(30));

        //订单合法性验证
        TradeRespose tradeRespose =  validateOrder(orderCommand);

        System.out.println("*".repeat(30));

        System.out.println(tradeRespose.getClOrderId());
        System.out.println(tradeRespose.getMarket());
        System.out.println(tradeRespose.getSecurityId());
        System.out.println(tradeRespose.getSide());
        System.out.println(tradeRespose.getQty());
        System.out.println(tradeRespose.getPrice());
        System.out.println(tradeRespose.getShareholderId());
        System.out.println(tradeRespose.getErrorMsg());
        System.out.println(tradeRespose.getRejectCode());

        System.out.println("*".repeat(30));

        BeanUtil.copyProperties(orderCommand, tradeRespose);
        if(tradeRespose.isSuccess()){
            System.out.println(tradeRespose.getClOrderId());
            System.out.println(tradeRespose.getMarket());
            System.out.println(tradeRespose.getSecurityId());
            System.out.println(tradeRespose.getSide());
            System.out.println(tradeRespose.getQty());
            System.out.println(tradeRespose.getPrice());
            System.out.println(tradeRespose.getShareholderId());
        }
        else {
            System.out.println(tradeRespose.getErrorMsg());
            System.out.println(tradeRespose.getRejectCode());
        }
    }
    private TradeRespose validateOrder(Order order) {
        if (order == null) {
            return TradeRespose.fail(1001, "订单为空");
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            return TradeRespose.fail(1002, "订单ID必须为16位");
        }
        if (order.getMarket() == null || !Set.of("XSHG", "XSHE", "BJSE").contains(order.getMarket())) {
            return TradeRespose.fail(1003, "市场代码非法");
        }
        if (order.getSecurityId() == null || order.getSecurityId().length() != 6) {
            return TradeRespose.fail(1004, "股票代码必须为6位");
        }
        if (order.getSide() == null || (!order.getSide().equals("B") && !order.getSide().equals("S"))) {
            return TradeRespose.fail(1005, "买卖方向必须为 B 或 S");
        }
        if (order.getQty() <= 0) {
            return TradeRespose.fail(1006, "数量必须大于0");
        }
        if (order.getPrice() <= 0) {
            return TradeRespose.fail(1007, "价格必须大于0");
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            return TradeRespose.fail(1008, "股东号必须为10位");
        }
        return TradeRespose.success();
    }

    private Order convertToOrderCommand(orderDTO order) {
        String clOrderId = order.getClOrderId() != null ? new String(order.getClOrderId()) : null;
        String market = order.getMarket() != null ? new String(order.getMarket()) : null;
        String securityId = order.getSecurityId() != null ? new String(order.getSecurityId()) : null;
        String side = order.getSide() != null ? new String(order.getSide()) : null;
        String shareholderId = order.getShareholderId() != null ? new String(order.getShareholderId()) : null;
        return new Order(clOrderId, market, securityId, side, order.getQty(), order.getPrice(), shareholderId);
    }
}
