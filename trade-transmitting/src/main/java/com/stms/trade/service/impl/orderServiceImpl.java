package com.stms.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.OrderIllegalCodeConstant;
import com.stms.common.constant.OrderIllegalReasonConstant;
import com.stms.common.constant.OrderStatusConstant;
import com.stms.common.constant.RedisConstant;
import com.stms.trade.domain.dto.orderDTO;
import com.stms.trade.domain.entity.Order;
import com.stms.trade.mapper.orderMapper;
import com.stms.trade.service.OrderEventPublisherService;
import com.stms.trade.service.RedisSecurityService;
import com.stms.trade.service.orderService;
import com.stms.trade.task.OrderCreatedEvent;
import com.stms.trade.utils.TradeRespose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class orderServiceImpl extends ServiceImpl<orderMapper, Order> implements orderService{

    @Resource
    private OrderEventPublisherService orderEventPublisherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisSecurityService redisSecurityService;

    private static final DefaultRedisScript<Long> ORDER_SUBMIT_SCRIPT ;       //声明lua脚本
    static {
        ORDER_SUBMIT_SCRIPT = new DefaultRedisScript<>();
        ORDER_SUBMIT_SCRIPT.setLocation(new ClassPathResource("create_order.lua"));      // 指定lua脚本位置
        ORDER_SUBMIT_SCRIPT.setResultType(Long.class);
    }

    //创建阻塞队列
    private BlockingQueue<Order> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //创建了一个单线程的、专门用于执行“创建订单”任务的后台线程池
    private static final ExecutorService ORDER_SUBMIT_EXECUTOR = Executors.newSingleThreadExecutor();

    //初始化，在实现类创建的时候就开始执行
    @PostConstruct
    public void init() {
        ORDER_SUBMIT_EXECUTOR.submit(new OrderHandler());
    }

    private class OrderHandler implements Runnable{
        @Override
        public void run() {
            while(true) {
                try {
                    //获取队列中的订单信息
                    Order orderCommand = orderTasks.take();
                    //创建订单
                    handleOrder(orderCommand);
                } catch (InterruptedException e) {
                    log.error("处理订单异常");
                }
            }
        }
    }


    @Override
    public TradeRespose submitOrder(orderDTO order) {

        //1.订单格式化转换
        Order orderCommand = convertToOrderCommand(order);

        //2. 短时间内重复订单ID检测
        if (redisSecurityService.isDuplicateOrderRequest(orderCommand.getClOrderId())) {
            log.warn("短时间内重复订单请求，直接拒绝，clOrderId={}", orderCommand.getClOrderId());
            return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_DUPLICATE_REQUEST,
                    OrderIllegalReasonConstant.ORDER_DUPLICATE_REQUEST_REASON);
        }

        //3.订单合法性验证
        TradeRespose tradeRespose =  validateOrder(orderCommand);
        BeanUtil.copyProperties(orderCommand, tradeRespose);

        //4.订单入库
        if(tradeRespose.isSuccess()){

            //4.1 设置订单状态信息为待处理
            orderCommand.setStatus(OrderStatusConstant.ORDER_STATUS_PENDING);

            //4.2 执行Lua脚本，在Redis中创建订单信息
            String redisKey = RedisConstant.ORDER_STATUS_KEY + orderCommand.getClOrderId();
            //构建订单信息的参数列表（排除id字段）
            List<String> args = new ArrayList<>();
            args.add(orderCommand.getClOrderId());
            args.add(orderCommand.getMarket());
            args.add(orderCommand.getSecurityId());
            args.add(orderCommand.getSide());
            args.add(String.valueOf(orderCommand.getQty()));
            args.add(String.valueOf(orderCommand.getPrice()));
            args.add(orderCommand.getShareholderId());
            args.add(OrderStatusConstant.ORDER_STATUS_PENDING);

            Long result = stringRedisTemplate.execute(
                    ORDER_SUBMIT_SCRIPT,  // 注入的Lua脚本
                    Collections.singletonList(redisKey),
                    args.toArray(new String[0])
            );

            //4.3 判断Lua执行结果
            if (result == null || result != 1) {
                // Lua 脚本执行失败（重复订单）
                return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_EXISTED,OrderIllegalReasonConstant.ORDER_EXISTED_REASON);
            }

            redisSecurityService.addOrderIdToBloomFilter(orderCommand.getClOrderId());

            boolean queued = orderTasks.add(orderCommand);

            if (!queued) {
                log.info("订单入队失败，订单ID：{}", orderCommand.getClOrderId());
                return TradeRespose.fail(OrderIllegalCodeConstant.SYSTEM_IS_BUSY, OrderIllegalReasonConstant.SYSTEM_IS_BUSY_REASON);
            }

            //5.使用RabbitMQ把订单数据发送到模拟风控系统，异步处理
            OrderCreatedEvent event = new OrderCreatedEvent(
                    orderCommand.getClOrderId(),
                    orderCommand.getMarket(),
                    orderCommand.getSecurityId(),
                    orderCommand.getSide(),
                    orderCommand.getQty(),
                    orderCommand.getPrice(),
                    orderCommand.getShareholderId()
            );
            orderEventPublisherService.publishOrderCreatedEvent(event);
        }

        //6.返回订单
        return tradeRespose;
    }

    private TradeRespose validateOrder(Order order) {
        if (order == null) {
            return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_IS_NULL, OrderIllegalReasonConstant.ORDER_IS_NULL_REASON);
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            return TradeRespose.fail(OrderIllegalCodeConstant.ORDER_CL_ORDER_ID_ILLEGAL, OrderIllegalReasonConstant.ORDER_CL_ORDER_ID_ILLEGAL_REASON);
        }
        if (order.getMarket() == null || !Set.of("XSHG", "XSHE", "BJSE").contains(order.getMarket())) {
            return TradeRespose.fail(OrderIllegalCodeConstant.MARKET_CODE_ILLEGAL, OrderIllegalReasonConstant.MARKET_CODE_ILLEGAL_REASON);
        }
        if (order.getSecurityId() == null || order.getSecurityId().length() != 6) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SECURITY_ID_ILLEGAL, OrderIllegalReasonConstant.SECURITY_ID_ILLEGAL_REASON);
        }
        if (order.getSide() == null || (!order.getSide().equals("B") && !order.getSide().equals("S"))) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SIDE_ILLEGAL, OrderIllegalReasonConstant.SIDE_ILLEGAL_REASON);
        }
        if (order.getQty() <= 0) {
            return TradeRespose.fail(OrderIllegalCodeConstant.QTY_ILLEGAL, OrderIllegalReasonConstant.QTY_ILLEGAL_REASON);
        }
        if (order.getPrice() <= 0) {
            return TradeRespose.fail(OrderIllegalCodeConstant.PRICE_ILLEGAL, OrderIllegalReasonConstant.PRICE_ILLEGAL_REASON);
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            return TradeRespose.fail(OrderIllegalCodeConstant.SHAREHOLDER_ID_ILLEGAL, OrderIllegalReasonConstant.SHAREHOLDER_ID_ILLEGAL_REASON);
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

    private void handleOrder(Order orderCommand) {
        save(orderCommand);
        lambdaUpdate()
                .eq(Order::getClOrderId, orderCommand.getClOrderId())
                .set(Order::getCanceledQty, 0L)
                .set(Order::getCumQty, 0L)
                .set(Order::getRemainingQty, orderCommand.getQty())
                .set(Order::getTradedAmount, 0.0)
                .update();
    }
}
