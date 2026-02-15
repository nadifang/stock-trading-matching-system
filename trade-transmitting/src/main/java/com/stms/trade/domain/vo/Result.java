package com.stms.trade.domain.vo;

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


    /**
     * 订单数据成功
     */
    public static Result ok(){
        return new Result(true,null,null,null);
    }

    /**
     * 订单数据回报
     */
    public static <T> Result<T> ok(Object data){
        return new Result(true,null,data,null);
    }

    /**
     * 分页数据回报
     */
    public static <T> Result<T> ok(Object data, Long total){
        return new Result(true,null,data,total);
    }

    /**
     * 错误信息回报
     */
    public static <T> Result<T> fail(Object errorMsg){
        return new Result(false,errorMsg,null,null);
    }
}
