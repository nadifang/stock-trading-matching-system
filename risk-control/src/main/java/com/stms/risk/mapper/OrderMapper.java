package com.stms.risk.mapper;

import com.stms.risk.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface OrderMapper {

    // 注意：此处为示意，实际应使用动态SQL处理集合参数
    @Select("<script>" +
            "SELECT * FROM orders WHERE security_id = #{securityId} " +
            "AND side = #{oppositeSide} " +
            "AND shareholder_id IN " +
            "<foreach item='item' collection='shareholderIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach> " +
            "AND status IN ('NEW', 'PARTIAL')" +
            "</script>")
    List<OrderEntity> findOppositeOrders(@Param("securityId") String securityId,
                                         @Param("oppositeSide") String oppositeSide,
                                         @Param("shareholderIds") List<String> shareholderIds);
}