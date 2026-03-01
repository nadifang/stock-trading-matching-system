package com.stms.export.controller;

import com.stms.export.domian.vo.Top10_cumQtyVO;
import com.stms.export.domian.vo.Top10_securityIdVO;
import com.stms.export.domian.vo.Top10_shareholderIdVO;
import com.stms.export.mapper.exportMapper;
import com.stms.export.service.CumQtyService;
import com.stms.export.service.SecurityTradeService;
import com.stms.export.service.ShareholderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/export-data")
public class DataExportController {

    @Resource
    private SecurityTradeService exportService;
    @Resource
    private ShareholderService shareholderService;
    @Resource
    private CumQtyService cumQtyService;

    @GetMapping("/securityId")
    public Top10_securityIdVO requestSecurityId(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime
            ) {

        log.info("开始导出股交易量数据: {} ~ {}", startTime, endTime);
        return exportService.getSecurityStatistics(startTime, endTime);
    }

    @GetMapping("/shareholderId")
    public Top10_shareholderIdVO requestShareholderId(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime
    ) {

        log.info("开始导出公司持股交易金额数据: {} ~ {}", startTime, endTime);
        return shareholderService.getShareholderStatistics(startTime, endTime);
    }

    @GetMapping("/qty")
    public Top10_cumQtyVO requestQty(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime
    ) {

        log.info("开始导出股交易额数据: {} ~ {}", startTime, endTime);
        return cumQtyService.getCumQtyStatistics(startTime, endTime);
    }
}
