package com.stms.risk.mapper;

import com.stms.risk.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface OrderMapper {

    boolean findOppositeOrders(@Param("securityId") String securityId,
                                         @Param("oppositeSide") String oppositeSide,
                                         @Param("shareholderId") String shareholderId);
}