package com.stms.match.controller;

import com.stms.match.domain.dto.OrderDTO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingController {

    @RequestMapping("/submit")
    public String submitOrder(@RequestBody OrderDTO order){
        return "";
    }
}
