package com.stms.export.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.export.domian.vo.Top10_cumQtyVO;
import com.stms.export.domian.vo.Top10_securityIdVO;
import com.stms.export.mapper.exportMapper;
import com.stms.export.service.CumQtyService;
import com.stms.trade.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
public class CumQtyServiceImpl extends ServiceImpl<exportMapper, Order> implements CumQtyService {

    @Resource
    private exportMapper exportMapper;

    @Override
    public Top10_cumQtyVO getCumQtyStatistics(LocalDate startTime, LocalDate endTime) {

        //1. 时间初始化
        LocalDateTime start = LocalDateTime.of(startTime, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(endTime, LocalTime.MAX);

        //2. 获取股票id
        List<String> top10SecurityIdVO = exportMapper.selectTop10_cumQty_securityId(start, end);
        log.info("股票id: {}", top10SecurityIdVO);

        String securityIdList = StringUtils.join(top10SecurityIdVO, ",");

        //3. 获取股票交易量
        List<Double> top10AmountVO = exportMapper.selectTop10_cumQty_security_amount(start, end);
        for (Double amount : top10AmountVO){
            double reAmount = amount / 2;
            top10AmountVO.set(top10AmountVO.indexOf(amount), reAmount);
        }
        log.info("股票交易量: {}", top10AmountVO);

        String qtyList = StringUtils.join(top10AmountVO, ",");

        //4. 返回数据
        return Top10_cumQtyVO.builder()
                .securityId(securityIdList)
                .cumQty(qtyList)
                .build();
    }
}
