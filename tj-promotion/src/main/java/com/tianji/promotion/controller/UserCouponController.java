package com.tianji.promotion.controller;


import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @ApiOperation("兑换码兑换优惠卷")
    @PostMapping("/{code}/exchange")
    public void exchangeCoupon(@PathVariable String code) {
        userCouponService.exchangeCoupon(code);
    }

    //该方法是给tj-trade服务 远程调用使用的 不会给前端调用

    /**
     * 查询可用优惠卷方案
     * @param courses 订单中的课程信息
     * @return
     */
    @ApiOperation("查询可用优惠卷方案")
    @PostMapping("available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> courses) {
        return userCouponService.findDiscountSolution(courses);
    }

}
