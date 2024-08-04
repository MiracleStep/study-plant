package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCouponDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-11
 */
public interface IUserCouponService extends IService<UserCoupon> {

    /**
     * 领取优惠卷
     * @param id
     */
    void receiveCoupon(Long id);

    /**
     * 兑换码兑换优惠卷
     * @param code
     */
    void exchangeCoupon(String code);

    /**
     * 校验并生成用户卷
     * @param userId
     * @param coupon
     */
    void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum);

    /**
     * 异步消息队列：校验并生成用户卷
     * @param msg
     */
    void checkAndCreateUserCouponNew(UserCouponDTO msg);

    /**
     * 查询可用优惠卷方案
     * @param courses
     * @return
     */
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses);

    /**
     * 计算订单优惠明细
     * @param orderCouponDTO
     * @return
     */
    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);
}
