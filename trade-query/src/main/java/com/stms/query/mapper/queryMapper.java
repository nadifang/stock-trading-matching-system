package com.stms.query.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stms.common.domain.entity.Order;
import com.stms.query.domain.dto.OrderAsk;
import com.stms.query.domain.dto.OrderBid;
import com.stms.query.domain.vo.OrderDetailVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface queryMapper extends BaseMapper<Order> {

    List<OrderDetailVO> selectLowest5(String securityId);

    List<OrderDetailVO> selectHighest5(String securityId);
}
