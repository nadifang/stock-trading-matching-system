package com.stms.shareholder.Service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stms.common.constant.RedisConstant;
import com.stms.query.service.OrderQueryService;
import com.stms.shareholder.Service.QueryShareholderService;
import com.stms.shareholder.Service.UserService;
import com.stms.shareholder.Utils.UserHolder;
import com.stms.shareholder.domain.dto.Result;
import com.stms.shareholder.domain.dto.UserDTO;
import com.stms.shareholder.domain.entity.User;
import com.stms.shareholder.domain.vo.ShareholderVO;
import com.stms.shareholder.mapper.queryShareholderMapper;
import com.stms.common.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QueryShareholderServiceImpl extends ServiceImpl<queryShareholderMapper, Order> implements QueryShareholderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;

    @Override
    public Result getInfo(String token) {

        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            log.error("用户信息不存在");
            return null;
        }
        User user = userService.getById(userDTO.getId());

        String redisKey = RedisConstant.USER_LOGIN_TOKEN_KEY + token;
        String orderJson = stringRedisTemplate.opsForValue().get(redisKey);

        if (stringRedisTemplate.hasKey(redisKey)) {
            if (StringUtils.isBlank(orderJson)) {
                log.error("用户未登录或redis解析错误");
                return Result.fail("用户未登录");
            }
        }else {
            log.error("用户未登录");
            return Result.fail("用户未登录");
        }

        String shareholderId = user.getShareholderId();
        List<ShareholderVO> shareholderVOList = getInfoByShareholderId(shareholderId);

        return Result.ok(shareholderVOList);
    }

    private List<ShareholderVO> getInfoByShareholderId(String shareholderId) {

        List<ShareholderVO> shareholderVOList = new ArrayList<>();

        List<Order> orderList = lambdaQuery().eq(Order::getShareholderId, shareholderId).list();
        for(Order order : orderList){
            ShareholderVO shareholderVO = ShareholderVO.builder()
                    .market(order.getMarket())
                    .securityId(order.getSecurityId())
                    .side(order.getSide())
                    .qty(order.getQty())
                    .price(order.getPrice())
                    .status(order.getStatus())
                    .cumQty(order.getCumQty())
                    .canceledQty(order.getCanceledQty())
                    .remainingQty(order.getRemainingQty())
                    .tradedAmount(order.getTradedAmount())
                    .createTime(order.getCreateTime())
                    .build();
            if (shareholderVO != null) {
                shareholderVOList.add(shareholderVO);
            }
        }
        return shareholderVOList;
    }
}
