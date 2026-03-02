package com.stms.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stms.trade.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface exportMapper extends BaseMapper<Order> {

    List<String> selectTop10_securityId(LocalDateTime startTime, LocalDateTime endTime);

    List<Double> selectTop10_security_amount(LocalDateTime startTime, LocalDateTime endTime);

    List<String> selectTop10_shareholderId(LocalDateTime startTime, LocalDateTime endTime);

    List<Double> selectTop10_shareholder_amount(LocalDateTime startTime, LocalDateTime endTime);

    List<String> selectTop10_cumQty_securityId(LocalDateTime startTime, LocalDateTime endTime);

    List<Double> selectTop10_cumQty_security_amount(LocalDateTime startTime, LocalDateTime endTime);
}
