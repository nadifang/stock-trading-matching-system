package com.stms.trade.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private boolean success;
    private Object errorMsg;
    private Object data;
    private Long total;
    public static Result ok(){
        return new Result(true,null,null,null);
    }
    public static Result ok(Object data){
        return new Result(true,null,data,null);
    }
    public static Result ok(Object data, Long total){
        return new Result(true,null,data,total);
    }
    public static Result fail(Object errorMsg){
        return new Result(false,errorMsg,null,null);
    }
}
