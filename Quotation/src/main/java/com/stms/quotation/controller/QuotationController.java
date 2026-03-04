package com.stms.quotation.controller;

import com.stms.common.Util.Result;
import com.stms.quotation.domain.vo.quotationVO;
import com.stms.quotation.service.quotationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/quotation")
public class QuotationController {

    @Resource
    private quotationService quotationService;

    @PostMapping("/publishQuotation")
    public Result getQuotation(@RequestBody quotationVO quotationVO) {
        log.info("行情参数：{}", quotationVO);
        return null;
    }

    @GetMapping("/getQuotation")
    public Result getQuotation(@RequestParam String securityId) {

        log.info("请求行情信息，股票号：{}", securityId);

        return quotationService.getInfo(securityId);
    }
}
