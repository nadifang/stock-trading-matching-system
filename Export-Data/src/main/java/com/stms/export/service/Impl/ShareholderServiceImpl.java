package com.stms.export.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stms.export.domian.vo.Top10_shareholderIdVO;
import com.stms.export.mapper.exportMapper;
import com.stms.export.service.ShareholderService;
import com.stms.trade.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class ShareholderServiceImpl extends ServiceImpl<exportMapper, Order> implements ShareholderService {

    @Resource
    private exportMapper exportMapper;

    @Override
    public Top10_shareholderIdVO getShareholderStatistics(LocalDate startTime, LocalDate endTime) {

        //1. 时间初始化
        LocalDateTime start = LocalDateTime.of(startTime, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(endTime, LocalTime.MAX);

        //2. 获取公司id
        List<String> top10ShareholderIdVO = exportMapper.selectTop10_shareholderId(start, end);
        log.info("公司id: {}", top10ShareholderIdVO);
        String shareholderIdList = StringUtils.join(top10ShareholderIdVO, ",");

        //3. 获取公司交易金额
        List<Double> top10AmountVO = exportMapper.selectTop10_shareholder_amount(start, end);
        log.info("公司交易金额: {}", top10AmountVO);
        String qtyList = StringUtils.join(top10AmountVO, ",");

        //4. 返回数据
        return Top10_shareholderIdVO.builder()
                .shareholderId(shareholderIdList)
                .amount(qtyList)
                .build();
    }
}
