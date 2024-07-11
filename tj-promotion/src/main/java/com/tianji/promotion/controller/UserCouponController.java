package com.tianji.promotion.controller;


import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author mirac
 * @since 2024-07-11
 */
@Api(tags = "用户卷相关接口")
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @ApiOperation("领取优惠卷")
    @PostMapping("{id}/receive")
    public void receiveCoupon(@PathVariable("id") Long id) {
        userCouponService.receiveCoupon(id);
    }

}
