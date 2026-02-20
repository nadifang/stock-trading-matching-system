package com.stms.trade.domain.vo;

import com.stms.common.constant.OrderStatusConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private boolean success;
    private T errorMsg;
    private T data;
    private Long total;
    private String status;


    /**
     * 订单数据成功
     */
    public static <T> Result<T> ok(){
        return new Result(true,null,null,null, OrderStatusConstant.ORDER_STATUS_PENDING);
    }

    /**
     * 订单数据回报
     */
    public static <T> Result<T> ok(Object data){
        return new Result(true,null,data,null,OrderStatusConstant.ORDER_STATUS_PENDING);
    }

    /**
     * 分页数据回报
     */
    public static <T> Result<T> ok(Object data, Long total){
        return new Result(true,null,data,total,OrderStatusConstant.ORDER_STATUS_PENDING);
    }

    /**
     * 错误信息回报
     */
    public static <T> Result<T> fail(Object errorMsg){
        return new Result(false,errorMsg,null,null,OrderStatusConstant.ORDER_STATUS_REJECTED);
    }

    /**
     * 撤单成功回报
     */
    public static <T> Result<T> canceled_ok(Object data){
        return new Result(true,null,data,null,OrderStatusConstant.ORDER_STATUS_CANCELED);
    }
}
